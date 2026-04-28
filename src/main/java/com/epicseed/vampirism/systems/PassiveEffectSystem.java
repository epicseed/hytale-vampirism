package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.model.Passive;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.SkillActionExecutor;
import com.epicseed.vampirism.skill.runtime.SkillConditionEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeDefinitions;
import com.epicseed.vampirism.skill.runtime.TriggerDispatcher;
import com.epicseed.vampirism.skill.runtime.TriggerEvent;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECS system that drives passive behaviors that cannot be expressed as static modifiers:
 *
 * <ol>
 *   <li><b>Persistent-effect passives</b> — passives that carry {@code actions} but no
 *       {@code triggers} (e.g. Night Vision) are treated as "always-on" effects.  The system
 *       re-applies their actions once per player until the effect is registered, then backs
 *       off to a periodic recheck in case the effect expired.</li>
 *   <li><b>{@code onCondition} trigger passives</b> — triggers whose resolved type is
 *       {@code "onCondition"} are polled every second.  When the associated condition
 *       transitions to <em>true</em> and the per-trigger cooldown has elapsed, the passive's
 *       actions are executed and the cooldown is restarted.  This covers skills like
 *       Bat Swarm (fires at ≤ 15 % HP with a 10-minute cooldown).</li>
 * </ol>
 *
 * <h3>Design constraints</h3>
 * <ul>
 *   <li>Runs on the WorldThread ECS tick — no background threads.</li>
 *   <li>Condition checks happen at most once per second per player to avoid hot-path overhead.</li>
 *   <li>Per-player cooldown and last-seen-state are tracked in {@link ConcurrentHashMap}s so
 *       disconnect cleanup is thread-safe.</li>
 * </ul>
 */
public class PassiveEffectSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Minimum seconds between full passive checks for a single player. */
    private static final float CHECK_INTERVAL_S = 1.0f;

    /** Seconds between periodic re-application of persistent (no-trigger) passive/skill effects. */
    private static final float PERSISTENT_REAPPLY_INTERVAL_S = 1.0f;

    /** Per-player accumulator for the periodic passive check interval. */
    private static final Map<UUID, Float> checkAccumulators = new ConcurrentHashMap<>();

    /**
     * Per-player cooldown tracking for {@code onCondition} triggers.
     * Key: UUID → (passiveId + "#" + triggerIndex) → last fire timestamp (ms).
     */
    private static final Map<UUID, Map<String, Long>> triggerLastFire = new ConcurrentHashMap<>();

    /**
     * Per-player tracking for the last time persistent passive effects were (re-)applied.
     * Key: UUID → (passiveId) → last apply timestamp (ms).
     */
    private static final Map<UUID, Map<String, Long>> persistentLastApply = new ConcurrentHashMap<>();
    /** Tracks whether connect-time trigger dispatch already ran for this player session. */
    private static final Map<UUID, Boolean> connectInitialized = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // ECS plumbing
    // -------------------------------------------------------------------------

    @Override
    public SystemGroup<EntityStore> getGroup() { return null; }

    @Override
    public Query<EntityStore> getQuery() { return Query.and(Player.getComponentType()); }

    @Override
    public void tick(float dt, int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {

        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

            Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;

            PlayerRef playerRefComp = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComp == null) return;

            UUID uuid = playerRefComp.getUuid();
            SkillRuntimeContext ctx = new SkillRuntimeContext(uuid, playerRef, store);
            if (!VampireStatusRegistry.get().isVampire(uuid)) {
                cleanupInactivePersistentOwners(uuid, ctx, Collections.emptySet());
                onPlayerDisconnect(uuid);
                return;
            }

            float accumulated = checkAccumulators.getOrDefault(uuid, 0f) + dt;
            if (accumulated < CHECK_INTERVAL_S) {
                checkAccumulators.put(uuid, accumulated);
                return;
            }
            checkAccumulators.put(uuid, 0f);

            Collection<Passive> unlocked = PassiveService.get().getUnlockedPassives(uuid);
            Set<String> activePersistentOwnerKeys = new HashSet<>();
            initializePlayerSession(ctx);

            for (String skillId : PlayerSkillRegistry.get().getUnlockedSkills(uuid)) {
                Skill skill = Vampirism.getInstance().GetSkillRegistry().GetSkill(skillId);
                if (skill != null) {
                    registerPersistentOwner(activePersistentOwnerKeys, "skill:" + skill.id, skill.triggers, skill.actions);
                    handleSkill(skill, ctx);
                }
            }

            if (!unlocked.isEmpty()) {
                for (Passive passive : unlocked) {
                    registerPersistentOwner(activePersistentOwnerKeys, "passive:" + passive.id, passive.triggers, passive.actions);
                    handlePassive(passive, ctx);
                }
            }
            cleanupInactivePersistentOwners(uuid, ctx, activePersistentOwnerKeys);

        } catch (Exception e) {
            LOGGER.atSevere().log("[PassiveEffectSystem] tick error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Passive dispatch
    // -------------------------------------------------------------------------

    private void initializePlayerSession(SkillRuntimeContext ctx) {
        UUID uuid = ctx.uuid();
        if (uuid == null) return;
        if (connectInitialized.putIfAbsent(uuid, Boolean.TRUE) != null) return;
        TriggerDispatcher.dispatch(TriggerEvent.onConnect(ctx));
    }

    private void handleSkill(Skill skill, SkillRuntimeContext ctx) {
        if (skill.actions == null || skill.actions.isEmpty()) return;
        handleOwner("skill:" + skill.id, "skill", skill.id,
                skill.triggers != null ? skill.triggers : Collections.emptyList(),
                skill.actions,
                ctx.withSkillScope(skill.id));
    }

    private void handlePassive(Passive passive, SkillRuntimeContext ctx) {
        if (passive.actions == null || passive.actions.isEmpty()) return;
        handleOwner("passive:" + passive.id, "passive", passive.id,
                passive.triggers != null ? passive.triggers : Collections.emptyList(),
                passive.actions,
                ctx.withPassiveScope(passive.id));
    }

    private void handleOwner(String ownerKey,
                             String ownerType,
                             String ownerId,
                             List<Map<String, Object>> triggers,
                             List<Map<String, Object>> actions,
                             SkillRuntimeContext ctx) {
        if (actions == null || actions.isEmpty()) return;
        if (triggers == null || triggers.isEmpty()) {
            handlePersistentOwner(ownerKey, ownerType, ownerId, actions, ctx);
            return;
        }

        for (int i = 0; i < triggers.size(); i++) {
            Map<String, Object> triggerSpec = triggers.get(i);
            Map<String, Object> resolved = SkillRuntimeDefinitions.resolveTrigger(triggerSpec);
            Object typeObj = resolved.get("type");
            if (!(typeObj instanceof String type)) continue;
            if ("onCondition".equals(type)) {
                handleOnConditionTrigger(ownerKey, ownerType, ownerId, actions, i, resolved, ctx);
            }
        }
    }

    private void registerPersistentOwner(@Nonnull Set<String> activeOwnerKeys,
                                         @Nonnull String ownerKey,
                                         List<Map<String, Object>> triggers,
                                         List<Map<String, Object>> actions) {
        if (actions == null || actions.isEmpty()) return;
        if (triggers == null || triggers.isEmpty()) {
            activeOwnerKeys.add(ownerKey);
        }
    }

    /**
     * Handles passives with no triggers: applies their actions once and then re-applies
     * every {@link #PERSISTENT_REAPPLY_INTERVAL_S} seconds to keep effects alive.
     */
    private void handlePersistentOwner(String ownerKey,
                                       String ownerType,
                                       String ownerId,
                                       List<Map<String, Object>> actions,
                                       SkillRuntimeContext ctx) {
        UUID uuid = ctx.uuid();
        if (uuid == null) return;

        Map<String, Long> playerApply = persistentLastApply.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        long lastApply = playerApply.getOrDefault(ownerKey, 0L);
        long nowMs = System.currentTimeMillis();

        if (nowMs - lastApply < (long)(PERSISTENT_REAPPLY_INTERVAL_S * 1000L)) return;

        boolean applied = SkillActionExecutor.executeAll(actions, ctx);
        if (applied) {
            playerApply.put(ownerKey, nowMs);
            LOGGER.atFine().log("[PassiveEffectSystem] Applied persistent " + ownerType + " '" + ownerId
                    + "' for " + uuid);
        }
    }

    private void cleanupInactivePersistentOwners(@Nonnull UUID uuid,
                                                 @Nonnull SkillRuntimeContext ctx,
                                                 @Nonnull Set<String> activeOwnerKeys) {
        Map<String, Long> playerApply = persistentLastApply.get(uuid);
        if (playerApply == null || playerApply.isEmpty()) {
            return;
        }
        Set<String> staleOwnerKeys = new HashSet<>(playerApply.keySet());
        staleOwnerKeys.removeAll(activeOwnerKeys);
        if (staleOwnerKeys.isEmpty()) {
            return;
        }

        for (String ownerKey : staleOwnerKeys) {
            removePersistentOwnerEffects(ownerKey, ctx);
            playerApply.remove(ownerKey);
        }

        if (playerApply.isEmpty()) {
            persistentLastApply.remove(uuid);
        }
    }

    private void removePersistentOwnerEffects(@Nonnull String ownerKey, @Nonnull SkillRuntimeContext ctx) {
        List<Map<String, Object>> actions = actionsForOwner(ownerKey);
        if (actions.isEmpty()) {
            return;
        }
        for (Map<String, Object> actionSpec : actions) {
            Map<String, Object> resolved = SkillRuntimeDefinitions.resolveAction(actionSpec);
            Object type = resolved.get("type");
            if (!"applyEffect".equals(type)) {
                continue;
            }
            Object effectId = resolved.get("effectId");
            if (!(effectId instanceof String effectIdString) || effectIdString.isBlank()) {
                continue;
            }

            Map<String, Object> removeSpec = new java.util.LinkedHashMap<>();
            removeSpec.put("type", "removeEffect");
            removeSpec.put("effectId", effectIdString);
            if (resolved.get("targetingId") instanceof String targetingId && !targetingId.isBlank()) {
                removeSpec.put("targetingId", targetingId);
            }
            SkillActionExecutor.execute(removeSpec, ctx);
        }
    }

    @Nonnull
    private List<Map<String, Object>> actionsForOwner(@Nonnull String ownerKey) {
        int separator = ownerKey.indexOf(':');
        if (separator <= 0 || separator >= ownerKey.length() - 1) {
            return Collections.emptyList();
        }
        String ownerType = ownerKey.substring(0, separator);
        String ownerId = ownerKey.substring(separator + 1);
        return switch (ownerType) {
            case "skill" -> {
                Skill skill = Vampirism.getInstance().GetSkillRegistry().GetSkill(ownerId);
                yield skill != null && skill.actions != null ? skill.actions : Collections.emptyList();
            }
            case "passive" -> {
                Passive passive = Vampirism.getInstance().GetPassiveRegistry().Get(ownerId);
                yield passive != null && passive.actions != null ? passive.actions : Collections.emptyList();
            }
            default -> Collections.emptyList();
        };
    }

    /**
     * Evaluates an {@code onCondition} trigger:
     * <ul>
     *   <li>Reads the embedded {@code conditionId} and evaluates it.</li>
     *   <li>If the condition is {@code true} and the per-trigger {@code cooldown} (seconds) has
     *       elapsed since the last fire, executes the passive's actions and records the fire
     *       timestamp.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private void handleOnConditionTrigger(String ownerKey,
                                          String ownerType,
                                          String ownerId,
                                          List<Map<String, Object>> actions,
                                          int triggerIndex,
                                          Map<String, Object> resolved,
                                          SkillRuntimeContext ctx) {
        UUID uuid = ctx.uuid();
        if (uuid == null) return;

        // Evaluate embedded condition
        String conditionId = resolved.get("conditionId") instanceof String s ? s : null;
        boolean conditionMet;
        if (conditionId != null) {
            conditionMet = SkillConditionEvaluator.evaluate(
                    Map.of("conditionId", conditionId), ctx);
        } else {
            // Inline condition list fallback
            Object inlineConds = resolved.get("conditions");
            if (inlineConds instanceof List<?> list) {
                conditionMet = SkillConditionEvaluator.evaluateAll((List<Map<String, Object>>) list, ctx);
            } else {
                conditionMet = false;
            }
        }

        if (!conditionMet) return;

        // Check cooldown
        double cooldownSec = resolved.get("cooldown") instanceof Number n ? n.doubleValue() : 0.0;
        String cooldownKey = ownerKey + "#" + triggerIndex;
        Map<String, Long> playerFires = triggerLastFire.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        long lastFire = playerFires.getOrDefault(cooldownKey, 0L);
        long nowMs    = System.currentTimeMillis();

        if (cooldownSec > 0.0 && (nowMs - lastFire) < (long)(cooldownSec * 1000L)) return;

        // Fire!
        playerFires.put(cooldownKey, nowMs);
        LOGGER.atInfo().log("[PassiveEffectSystem] onCondition fired for " + ownerType + "='" + ownerId
                + "' trigger=" + triggerIndex + " player=" + uuid);
        SkillActionExecutor.executeAll(actions, ctx);
    }

    // -------------------------------------------------------------------------
    // Connect / disconnect hooks
    // -------------------------------------------------------------------------

    /**
     * Called when a player connects.  Immediately applies persistent (no-trigger) passive
     * effects so they are active from the start of the session without waiting for the first
     * interval.
     */
    public static void onPlayerConnect(UUID uuid, Ref<EntityStore> playerRef, Store<EntityStore> store) {
        try {
            Collection<Passive> unlocked = PassiveService.get().getUnlockedPassives(uuid);
            if (unlocked.isEmpty()) return;

            SkillRuntimeContext ctx = new SkillRuntimeContext(uuid, playerRef, store);
            for (Passive passive : unlocked) {
                List<Map<String, Object>> triggers = passive.triggers != null
                        ? passive.triggers : Collections.emptyList();
                if (!triggers.isEmpty()) continue;
                if (passive.actions == null || passive.actions.isEmpty()) continue;

                boolean applied = SkillActionExecutor.executeAll(passive.actions, ctx);
                if (applied) {
                    LOGGER.atInfo().log("[PassiveEffectSystem] Connect-time passive '" + passive.id
                            + "' applied for " + uuid);
                }
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[PassiveEffectSystem] onPlayerConnect error: " + e.getMessage());
        }
    }

    /** Removes all per-player cooldown / apply tracking for a disconnecting player. */
    public static void onPlayerDisconnect(UUID uuid) {
        checkAccumulators.remove(uuid);
        triggerLastFire.remove(uuid);
        persistentLastApply.remove(uuid);
        connectInitialized.remove(uuid);
    }
}
