package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.EffectDef;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeDefinitions;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.NPCMarkerComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.AnimationSlot;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the short channel used by BloodSucker-style feed abilities.
 */
public class BloodFeedSystem extends EntityTickingSystem<EntityStore> {

    private static final float DEFAULT_CHANNEL_DURATION_S = 1.5f;
    private static final double DEFAULT_MAX_RANGE = 1.0;
    private static final float DEFAULT_BITE_DAMAGE = 6.0f;
    private static final float DEFAULT_BLOOD_GAIN = 10f;
    private static final float DEFAULT_EXECUTE_THRESHOLD = 0.25f;
    private static final float MIN_SPEED_MULTIPLIER = 0.1f;
    private static final double RANGE_EPSILON = 0.25d;
    private static final double CAST_CANCEL_MOVE_DISTANCE = 0.2d;
    private static final String BLOOD_SUCKER_ABILITY_ID = "BloodSucker";
    private static final String CONSUMABLE_MARKER_EFFECT_ID = "blood_feed_consumable_marker_effect";
    private static final double CONSUMABLE_MARKER_RANGE = 8.0d;
    private static final float CONSUMABLE_MARKER_SCAN_INTERVAL_S = 0.5f;
    private static final float CONSUMABLE_MARKER_DURATION_S = 1.25f;

    private static final Map<UUID, FeedSession> activeFeeds = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> markerScanAccumulators = new ConcurrentHashMap<>();

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    public static boolean startChannel(@Nonnull SkillRuntimeContext ctx, @Nonnull Map<String, Object> action) {
        UUID uuid = ctx.uuid();
        Ref<EntityStore> targetRef = ctx.targetRef();
        if (uuid == null || targetRef == null || targetRef.equals(ctx.ref())) {
            return false;
        }

        Ability ability = resolveAbility(ctx.currentAbilityId());
        double maxRange = resolveMaxRange(action, ability);
        if (!isWithinRange(ctx.ref(), targetRef, ctx.store(), maxRange)) {
            return false;
        }

        EntityStatMap targetStats = (EntityStatMap) ctx.store().getComponent(targetRef, EntityStatMap.getComponentType());
        if (targetStats == null) {
            return false;
        }
        EntityStatValue health = targetStats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.get() <= 0f) {
            return false;
        }

        SkillRuntimeContext thresholdCtx = ctx.currentAbilityId() != null && !ctx.currentAbilityId().isBlank()
                ? ctx.withActivatedAbility(ctx.currentAbilityId())
                : ctx;
        float executeThreshold = ModifierRegistry.get().compute(
                resolveStatName(action.get("executeThresholdStatId"), VampireStatType.ABILITY_EXECUTE_HEALTH_THRESHOLD),
                resolveLiteral(action, "executeThreshold", DEFAULT_EXECUTE_THRESHOLD),
                thresholdCtx.modifierContext());
        float hpPercent = health.getMax() > 0f ? health.get() / health.getMax() : 0f;
        if (hpPercent > executeThreshold) {
            return false;
        }

        float durationSeconds = resolveDurationSeconds(action, ability);
        float feedSpeed = resolveMultiplier(action.get("feedSpeedStatId"), VampireStatType.FEEDING_SPEED, 1f, ctx);
        float effectiveDuration = durationSeconds / Math.max(MIN_SPEED_MULTIPLIER, feedSpeed);

        FeedSession previous = activeFeeds.remove(uuid);
        if (previous != null) {
            cleanupChannelEffects(previous, ctx.store(), ctx.ref());
        }

        FeedSession session = new FeedSession(
                ctx.currentAbilityId(),
                targetRef,
                effectiveDuration,
                maxRange,
                lockPosition(ctx.ref(), ctx.store()),
                lockPosition(targetRef, ctx.store()),
                resolveLiteralOrStat(action, "damage", "damageStatId", DEFAULT_BITE_DAMAGE, ctx),
                normalizeBloodValue(resolveLiteral(action, "bloodGain", DEFAULT_BLOOD_GAIN)),
                resolveStatName(action.get("bloodGainStatId"), VampireStatType.BLOOD_COLLECTION_RATE),
                resolveLiteral(action, "executeThreshold", DEFAULT_EXECUTE_THRESHOLD),
                resolveStatName(action.get("executeThresholdStatId"), VampireStatType.ABILITY_EXECUTE_HEALTH_THRESHOLD),
                action.get("casterAnimationId") instanceof String s && !s.isBlank() ? s : null,
                action.get("casterEffectId") instanceof String s && !s.isBlank() ? s : null,
                action.get("targetEffectId") instanceof String s && !s.isBlank() ? s : null);
        applyChannelEffects(session, ctx.store(), ctx.ref());
        activeFeeds.put(uuid, session);
        return true;
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        if (uuid != null) {
            activeFeeds.remove(uuid);
            markerScanAccumulators.remove(uuid);
        }
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

        if (store.getComponent(playerRef, Player.getComponentType()) == null) return;

        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) return;

        UUID uuid = playerRefComponent.getUuid();
        FeedSession session = activeFeeds.get(uuid);

        if (!VampireStatusRegistry.get().isVampire(uuid)) {
            markerScanAccumulators.remove(uuid);
            if (session == null) return;
            activeFeeds.remove(uuid);
            cleanupChannelEffects(session, store, playerRef);
            return;
        }

        refreshConsumableMarkers(uuid, playerRef, dt, store);

        if (session == null) return;

        if (!isSessionStillValid(playerRef, session, store)) {
            activeFeeds.remove(uuid);
            cleanupChannelEffects(session, store, playerRef);
            return;
        }

        maintainTargetLock(session, store);

        session.remainingSeconds -= dt;
        if (session.remainingSeconds > 0f) return;

        activeFeeds.remove(uuid);
        commandBuffer.run(bufferStore -> completeFeed(uuid, playerRef, session, bufferStore));
    }

    private static void completeFeed(@Nonnull UUID uuid,
                                     @Nonnull Ref<EntityStore> playerRef,
                                     @Nonnull FeedSession session,
                                     @Nonnull Store<EntityStore> store) {
        SkillRuntimeContext baseCtx = new SkillRuntimeContext(uuid, playerRef, session.targetRef, store);
        SkillRuntimeContext ctx = session.abilityId != null && !session.abilityId.isBlank()
                ? baseCtx.withActivatedAbility(session.abilityId)
                : baseCtx;

        EntityStatMap targetStats = (EntityStatMap) ctx.store().getComponent(session.targetRef, EntityStatMap.getComponentType());
        if (targetStats == null) {
            return;
        }

        EntityStatValue health = targetStats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.get() <= 0f) {
            cleanupChannelEffects(session, store, playerRef);
            return;
        }

        float executeThreshold = ModifierRegistry.get().compute(
                session.executeThresholdStat,
                session.executeThreshold,
                ctx.modifierContext());
        float hpPercent = health.getMax() > 0f ? health.get() / health.getMax() : 0f;
        float damageAmount = hpPercent <= executeThreshold
                ? Math.max(health.get(), health.getMax()) + 9999f
                : Math.max(0f, session.damage);
        if (!applyDamage(ctx.ref(), session.targetRef, damageAmount, store)) {
            cleanupChannelEffects(session, store, playerRef);
            return;
        }
        boolean targetKilled = isTargetDead(session.targetRef, store);

        float bloodGainMultiplier = ModifierRegistry.get().compute(
                session.bloodGainStat,
                1f,
                ctx.modifierContext());
        int bloodGain = Math.max(0, Math.round(session.baseBloodGain * Math.max(0f, bloodGainMultiplier)));
        if (bloodGain > 0) {
            VampireVitalitySystem.addBlood(ctx.ref(), bloodGain);
        }
        cleanupChannelEffects(session, store, playerRef);
        PassiveService.get().onFeed(ctx);
        if (targetKilled) {
            NightHuntService.onPlayerKilledMarkedPrey(uuid, playerRef, session.targetRef, store);
            PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            String playerName = playerRefComponent != null ? playerRefComponent.getUsername() : uuid.toString();
            VampireInfectionSystem.completeAscension(uuid, playerName, playerRef, store);
        }
    }

    private static boolean isSessionStillValid(@Nonnull Ref<EntityStore> playerRef,
                                               @Nonnull FeedSession session,
                                               @Nonnull Store<EntityStore> store) {
        EntityStatMap targetStats = (EntityStatMap) store.getComponent(session.targetRef, EntityStatMap.getComponentType());
        if (targetStats == null) {
            return false;
        }
        EntityStatValue health = targetStats.get(DefaultEntityStatTypes.getHealth());
        return health != null
                && health.get() > 0f
                && !hasCasterMoved(playerRef, session, store)
                && isWithinRange(playerRef, session.targetRef, store, session.maxRange);
    }

    private static boolean hasCasterMoved(@Nonnull Ref<EntityStore> playerRef,
                                          @Nonnull FeedSession session,
                                          @Nonnull Store<EntityStore> store) {
        if (session.casterStartPosition == null) {
            return false;
        }
        TransformComponent transform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return true;
        }

        var position = transform.getPosition();
        double dx = position.x - session.casterStartPosition.x;
        double dz = position.z - session.casterStartPosition.z;
        double movedSq = dx * dx + dz * dz;
        return movedSq > CAST_CANCEL_MOVE_DISTANCE * CAST_CANCEL_MOVE_DISTANCE;
    }

    private static boolean isWithinRange(@Nonnull Ref<EntityStore> sourceRef,
                                         @Nonnull Ref<EntityStore> targetRef,
                                         @Nonnull Store<EntityStore> store,
                                         double maxRange) {
        TransformComponent sourceTransform = (TransformComponent) store.getComponent(sourceRef, TransformComponent.getComponentType());
        TransformComponent targetTransform = (TransformComponent) store.getComponent(targetRef, TransformComponent.getComponentType());
        if (sourceTransform == null || targetTransform == null) {
            return false;
        }

        var source = sourceTransform.getPosition();
        var target = targetTransform.getPosition();
        double dx = source.x - target.x;
        double dy = source.y - target.y;
        double dz = source.z - target.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double allowed = maxRange + RANGE_EPSILON;
        return distanceSq <= allowed * allowed;
    }

    private static boolean applyDamage(@Nonnull Ref<EntityStore> sourceRef,
                                       @Nonnull Ref<EntityStore> targetRef,
                                       float amount,
                                       @Nonnull Store<EntityStore> store) {
        if (amount <= 0f) {
            return false;
        }
        Damage damage = new Damage(new Damage.EntitySource(sourceRef), DamageCause.PHYSICAL, amount);
        DamageSystems.executeDamage(targetRef, store, damage);
        return true;
    }

    private static void applyChannelEffects(@Nonnull FeedSession session,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> playerRef) {
        playCasterAnimation(playerRef, session.casterAnimationId, store);
        applyChannelEffect(playerRef, session.casterEffectId, session.remainingSeconds, store);
        applyChannelEffect(session.targetRef, session.targetEffectId, session.remainingSeconds, store);
    }

    private static void cleanupChannelEffects(@Nonnull FeedSession session,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Ref<EntityStore> playerRef) {
        stopCasterAnimation(playerRef, session.casterAnimationId, store);
        removeChannelEffect(playerRef, session.casterEffectId, store);
        removeChannelEffect(session.targetRef, session.targetEffectId, store);
    }

    private static void playCasterAnimation(@Nonnull Ref<EntityStore> playerRef,
                                            @Nullable String animationId,
                                            @Nonnull Store<EntityStore> store) {
        if (animationId == null || animationId.isBlank()) return;
        AnimationUtils.playAnimation(playerRef, AnimationSlot.Emote, null, animationId, true, store);
    }

    private static void stopCasterAnimation(@Nonnull Ref<EntityStore> playerRef,
                                            @Nullable String animationId,
                                            @Nonnull Store<EntityStore> store) {
        if (animationId == null || animationId.isBlank()) return;
        AnimationUtils.stopAnimation(playerRef, AnimationSlot.Emote, true, store);
    }

    @Nullable
    private static Vector3d lockPosition(@Nonnull Ref<EntityStore> targetRef, @Nonnull Store<EntityStore> store) {
        TransformComponent transform = (TransformComponent) store.getComponent(targetRef, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }

    private static int normalizeBloodValue(float value) {
        float normalized = Math.max(0f, value);
        if (normalized <= 1f) {
            normalized *= VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS;
        }
        return Math.max(0, Math.round(normalized));
    }

    private static void maintainTargetLock(@Nonnull FeedSession session, @Nonnull Store<EntityStore> store) {
        if (session.lockedPosition == null) return;
        TransformComponent transform = (TransformComponent) store.getComponent(session.targetRef, TransformComponent.getComponentType());
        if (transform != null) {
            transform.teleportPosition(session.lockedPosition);
        }
        Velocity velocity = (Velocity) store.getComponent(session.targetRef, Velocity.getComponentType());
        if (velocity != null) {
            velocity.setZero();
            velocity.setClient(0d, 0d, 0d);
        }
    }

    private static void refreshConsumableMarkers(@Nonnull UUID uuid,
                                                 @Nonnull Ref<EntityStore> playerRef,
                                                 float dt,
                                                 @Nonnull Store<EntityStore> store) {
        if (!PlayerSkillRegistry.get().hasSkill(uuid, BLOOD_SUCKER_ABILITY_ID)
                && !VampireInfectionSystem.allowsTemporaryAbility(uuid, BLOOD_SUCKER_ABILITY_ID)) {
            markerScanAccumulators.remove(uuid);
            return;
        }

        float accumulated = markerScanAccumulators.getOrDefault(uuid, 0f) + Math.max(0f, dt);
        if (accumulated < CONSUMABLE_MARKER_SCAN_INTERVAL_S) {
            markerScanAccumulators.put(uuid, accumulated);
            return;
        }
        markerScanAccumulators.put(uuid, 0f);

        TransformComponent casterTransform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        if (casterTransform == null) {
            return;
        }

        SkillRuntimeContext ctx = new SkillRuntimeContext(uuid, playerRef, null, store).withActivatedAbility(BLOOD_SUCKER_ABILITY_ID);
        float executeThreshold = resolveConsumableMarkerThreshold(ctx);
        Vector3d center = new Vector3d(casterTransform.getPosition());

        var nearby = TargetUtil.getAllEntitiesInSphere(center, CONSUMABLE_MARKER_RANGE, store);
        if (nearby == null || nearby.isEmpty()) {
            return;
        }

        for (Ref<EntityStore> targetRef : nearby) {
            if (!isConsumableMarkerCandidate(playerRef, targetRef, executeThreshold, store)) {
                continue;
            }
            applyTimedEffect(targetRef, CONSUMABLE_MARKER_EFFECT_ID, CONSUMABLE_MARKER_DURATION_S, OverlapBehavior.EXTEND, store);
        }
    }

    private static float resolveConsumableMarkerThreshold(@Nonnull SkillRuntimeContext ctx) {
        Ability ability = resolveAbility(BLOOD_SUCKER_ABILITY_ID);
        Map<String, Object> action = resolveFeedAction(ability);
        return ModifierRegistry.get().compute(
                resolveStatName(action.get("executeThresholdStatId"), VampireStatType.ABILITY_EXECUTE_HEALTH_THRESHOLD),
                resolveLiteral(action, "executeThreshold", DEFAULT_EXECUTE_THRESHOLD),
                ctx.modifierContext());
    }

    @Nonnull
    private static Map<String, Object> resolveFeedAction(@Nullable Ability ability) {
        if (ability == null || ability.actions == null) {
            return Map.of();
        }
        for (Map<String, Object> actionSpec : ability.actions) {
            Map<String, Object> resolved = SkillRuntimeDefinitions.resolveAction(actionSpec);
            if ("startFeedChannel".equals(resolved.get("type"))) {
                return resolved;
            }
        }
        return Map.of();
    }

    private static boolean isConsumableMarkerCandidate(@Nonnull Ref<EntityStore> playerRef,
                                                       @Nonnull Ref<EntityStore> targetRef,
                                                       float executeThreshold,
                                                       @Nonnull Store<EntityStore> store) {
        if (targetRef.equals(playerRef)) {
            return false;
        }
        if (store.getComponent(targetRef, Player.getComponentType()) != null) {
            return false;
        }
        if (store.getComponent(targetRef, NPCMarkerComponent.getComponentType()) == null) {
            return false;
        }

        EntityStatMap targetStats = (EntityStatMap) store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (targetStats == null) {
            return false;
        }
        EntityStatValue health = targetStats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.get() <= 0f || health.getMax() <= 0f) {
            return false;
        }

        float hpPercent = health.get() / health.getMax();
        return hpPercent <= Math.max(0f, executeThreshold);
    }

    private static boolean isTargetDead(@Nonnull Ref<EntityStore> targetRef, @Nonnull Store<EntityStore> store) {
        EntityStatMap targetStats = (EntityStatMap) store.getComponent(targetRef, EntityStatMap.getComponentType());
        if (targetStats == null) {
            return true;
        }
        EntityStatValue health = targetStats.get(DefaultEntityStatTypes.getHealth());
        return health == null || health.get() <= 0f;
    }

    private static void applyChannelEffect(@Nonnull Ref<EntityStore> ref,
                                           @Nullable String effectDefId,
                                           float durationSeconds,
                                           @Nonnull Store<EntityStore> store) {
        applyTimedEffect(ref, effectDefId, durationSeconds, OverlapBehavior.OVERWRITE, store);
    }

    private static void applyTimedEffect(@Nonnull Ref<EntityStore> ref,
                                         @Nullable String effectDefId,
                                         float durationSeconds,
                                         @Nonnull OverlapBehavior overlapBehavior,
                                         @Nonnull Store<EntityStore> store) {
        if (effectDefId == null || effectDefId.isBlank()) return;
        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectDefId);
        if (effectDef == null) return;
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) return;
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
        if (effect == null) return;
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ec == null) return;
        ec.addEffect(ref, effectIndex, effect, Math.max(0.1f, durationSeconds), overlapBehavior, store);
    }

    private static void removeChannelEffect(@Nonnull Ref<EntityStore> ref,
                                            @Nullable String effectDefId,
                                            @Nonnull Store<EntityStore> store) {
        if (effectDefId == null || effectDefId.isBlank()) return;
        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectDefId);
        if (effectDef == null) return;
        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) return;
        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (ec == null || !ec.hasEffect(effectIndex)) return;
        ec.removeEffect(ref, effectIndex, store);
    }

    @Nullable
    private static Ability resolveAbility(@Nullable String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }
        return Vampirism.getInstance().GetAbilityRegistry().Get(abilityId);
    }

    private static float resolveDurationSeconds(@Nonnull Map<String, Object> action, @Nullable Ability ability) {
        if (action.get("durationSeconds") instanceof Number n) {
            return Math.max(0f, n.floatValue());
        }
        if (ability != null && ability.channelDuration > 0f) {
            return ability.channelDuration;
        }
        return DEFAULT_CHANNEL_DURATION_S;
    }

    private static double resolveMaxRange(@Nonnull Map<String, Object> action, @Nullable Ability ability) {
        if (action.get("maxRange") instanceof Number n) {
            return Math.max(0d, n.doubleValue());
        }
        if (ability != null && ability.targeting != null) {
            Map<String, Object> targeting = SkillRuntimeDefinitions.resolveTargeting(ability.targeting);
            if (targeting.get("maxRange") instanceof Number n) {
                return Math.max(0d, n.doubleValue());
            }
        }
        return DEFAULT_MAX_RANGE;
    }

    private static float resolveLiteral(@Nonnull Map<String, Object> action, @Nonnull String key, float fallback) {
        return action.get(key) instanceof Number n ? n.floatValue() : fallback;
    }

    private static float resolveLiteralOrStat(@Nonnull Map<String, Object> action,
                                              @Nonnull String literalKey,
                                              @Nonnull String statKey,
                                              float fallback,
                                              @Nonnull SkillRuntimeContext ctx) {
        Object rawStat = action.get(statKey);
        if (rawStat instanceof String statName && !statName.isBlank()) {
            try {
                VampireStatType stat = VampireStatType.valueOf(statName);
                return ModifierRegistry.get().compute(stat, fallback, ctx.modifierContext());
            } catch (IllegalArgumentException ignored) {
                return resolveLiteral(action, literalKey, fallback);
            }
        }
        return resolveLiteral(action, literalKey, fallback);
    }

    private static float resolveMultiplier(@Nullable Object rawStat,
                                           @Nonnull VampireStatType fallbackStat,
                                           float fallback,
                                           @Nonnull SkillRuntimeContext ctx) {
        VampireStatType stat = resolveStatName(rawStat, fallbackStat);
        return ModifierRegistry.get().compute(stat, fallback, ctx.modifierContext());
    }

    @Nonnull
    private static VampireStatType resolveStatName(@Nullable Object rawStat, @Nonnull VampireStatType fallback) {
        if (rawStat instanceof String statName && !statName.isBlank()) {
            try {
                return VampireStatType.valueOf(statName);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static final class FeedSession {
        private final String abilityId;
        private final Ref<EntityStore> targetRef;
        private float remainingSeconds;
        private final double maxRange;
        private final Vector3d casterStartPosition;
        private final Vector3d lockedPosition;
        private final float damage;
        private final int baseBloodGain;
        private final VampireStatType bloodGainStat;
        private final float executeThreshold;
        private final VampireStatType executeThresholdStat;
        private final String casterAnimationId;
        private final String casterEffectId;
        private final String targetEffectId;

        private FeedSession(@Nullable String abilityId,
                            @Nonnull Ref<EntityStore> targetRef,
                            float remainingSeconds,
                            double maxRange,
                            @Nullable Vector3d casterStartPosition,
                            @Nullable Vector3d lockedPosition,
                            float damage,
                            int baseBloodGain,
                            @Nonnull VampireStatType bloodGainStat,
                            float executeThreshold,
                            @Nonnull VampireStatType executeThresholdStat,
                            @Nullable String casterAnimationId,
                            @Nullable String casterEffectId,
                            @Nullable String targetEffectId) {
            this.abilityId = abilityId;
            this.targetRef = targetRef;
            this.remainingSeconds = remainingSeconds;
            this.maxRange = maxRange;
            this.casterStartPosition = casterStartPosition;
            this.lockedPosition = lockedPosition;
            this.damage = damage;
            this.baseBloodGain = baseBloodGain;
            this.bloodGainStat = bloodGainStat;
            this.executeThreshold = executeThreshold;
            this.executeThresholdStat = executeThresholdStat;
            this.casterAnimationId = casterAnimationId;
            this.casterEffectId = casterEffectId;
            this.targetEffectId = targetEffectId;
        }
    }
}
