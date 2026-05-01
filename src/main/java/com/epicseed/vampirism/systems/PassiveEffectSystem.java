package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.interop.VampirismClassifications;
import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.runtime.PassiveTriggerRuntimeService;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.PersistentPassiveEffectService;
import com.epicseed.vampirism.skill.runtime.PersistentPassiveOwnerKey;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
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
import java.util.Objects;

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

    /** Per-player accumulator for the periodic passive check interval. */
    private static final Map<UUID, Float> checkAccumulators = new ConcurrentHashMap<>();
    private final PassiveService passiveService;
    private final ProgressionDefinitionProvider definitionProvider;
    private final SkillProgressionAccess progressionAccess;

    public PassiveEffectSystem(@Nonnull PassiveService passiveService,
                               @Nonnull ProgressionDefinitionProvider definitionProvider,
                               @Nonnull SkillProgressionAccess progressionAccess) {
        this.passiveService = Objects.requireNonNull(passiveService, "passiveService");
        this.definitionProvider = Objects.requireNonNull(definitionProvider, "definitionProvider");
        this.progressionAccess = Objects.requireNonNull(progressionAccess, "progressionAccess");
    }

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
            if (!VampirismClassifications.isVampiric(uuid)) {
                PersistentPassiveEffectService.cleanupInactiveOwners(uuid, ctx, Collections.emptySet());
                onPlayerDisconnect(uuid);
                return;
            }

            float accumulated = checkAccumulators.getOrDefault(uuid, 0f) + dt;
            if (accumulated < CHECK_INTERVAL_S) {
                checkAccumulators.put(uuid, accumulated);
                return;
            }
            checkAccumulators.put(uuid, 0f);

            Collection<Passive> unlocked = passiveService.getUnlockedPassives(uuid);
            Set<String> activePersistentOwnerKeys = new HashSet<>();
            PassiveTriggerRuntimeService.initializePlayerSession(ctx);

            for (String skillId : progressionAccess.getUnlockedSkillIds(uuid)) {
                Skill skill = definitionProvider.getSkill(skillId);
                if (skill != null) {
                    PersistentPassiveEffectService.registerPersistentOwner(
                            activePersistentOwnerKeys,
                            PersistentPassiveOwnerKey.skill(skill.id),
                            skill.triggers,
                            skill.actions);
                    handleSkill(skill, ctx);
                }
            }

            if (!unlocked.isEmpty()) {
                for (Passive passive : unlocked) {
                    PersistentPassiveEffectService.registerPersistentOwner(
                            activePersistentOwnerKeys,
                            PersistentPassiveOwnerKey.passive(passive.id),
                            passive.triggers,
                            passive.actions);
                    handlePassive(passive, ctx);
                }
            }
            PersistentPassiveEffectService.cleanupInactiveOwners(uuid, ctx, activePersistentOwnerKeys);

        } catch (Exception e) {
            LOGGER.atSevere().log("[PassiveEffectSystem] tick error: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Passive dispatch
    // -------------------------------------------------------------------------

    private void handleSkill(Skill skill, SkillRuntimeContext ctx) {
        if (skill.actions == null || skill.actions.isEmpty()) return;
        handleOwner(PersistentPassiveOwnerKey.skill(skill.id),
                skill.triggers != null ? skill.triggers : Collections.emptyList(),
                skill.actions,
                ctx.withSkillScope(skill.id));
    }

    private void handlePassive(Passive passive, SkillRuntimeContext ctx) {
        if (passive.actions == null || passive.actions.isEmpty()) return;
        handleOwner(PersistentPassiveOwnerKey.passive(passive.id),
                passive.triggers != null ? passive.triggers : Collections.emptyList(),
                passive.actions,
                ctx.withPassiveScope(passive.id));
    }

    private void handleOwner(PersistentPassiveOwnerKey ownerKey,
                             List<Map<String, Object>> triggers,
                             List<Map<String, Object>> actions,
                             SkillRuntimeContext ctx) {
        if (actions == null || actions.isEmpty()) return;
        if (PersistentPassiveEffectService.isPersistentOwner(triggers, actions)) {
            PersistentPassiveEffectService.handleOwner(ownerKey, actions, ctx);
            return;
        }
        PassiveTriggerRuntimeService.handleOwner(ownerKey, triggers, actions, ctx);
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

                PersistentPassiveEffectService.handleOwner(
                        PersistentPassiveOwnerKey.passive(passive.id),
                        passive.actions,
                        ctx);
            }
        } catch (Exception e) {
            LOGGER.atWarning().log("[PassiveEffectSystem] onPlayerConnect error: " + e.getMessage());
        }
    }

    /** Removes all per-player cooldown / apply tracking for a disconnecting player. */
    public static void onPlayerDisconnect(UUID uuid) {
        checkAccumulators.remove(uuid);
        PassiveTriggerRuntimeService.clearPlayer(uuid);
        PersistentPassiveEffectService.clearPlayer(uuid);
    }
}
