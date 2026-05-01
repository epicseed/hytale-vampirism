package com.epicseed.vampirism.systems;
import com.epicseed.vampirism.modifier.ModifierContext;

import com.epicseed.vampirism.domain.blood.ConsumableMarkerService;
import com.epicseed.vampirism.domain.blood.FeedChannelPresentationService;
import com.epicseed.vampirism.domain.blood.FeedCompletionService;
import com.epicseed.vampirism.domain.blood.FeedEligibility;
import com.epicseed.vampirism.domain.blood.FeedSession;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.interop.VampirismClassifications;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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

    private static final Map<UUID, FeedSession> activeFeeds = new ConcurrentHashMap<>();

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    public static boolean startChannel(@Nonnull SkillRuntimeContext ctx, @Nonnull Map<String, Object> action) {
        UUID uuid = ctx.uuid();
        Ref<EntityStore> targetRef = ctx.targetRef();
        if (uuid == null || targetRef == null || targetRef.equals(ctx.ref())) {
            return false;
        }

        Ability ability = resolveAbility(ctx.currentAbilityId());
        double maxRange = resolveMaxRange(action, ability);
        if (!FeedEligibility.isWithinRange(ctx.ref(), targetRef, ctx.store(), maxRange, RANGE_EPSILON)) {
            return false;
        }

        EntityStatValue health = FeedEligibility.resolveHealth(targetRef, ctx.store());
        if (health == null || health.get() <= 0f) {
            return false;
        }

        SkillRuntimeContext thresholdCtx = ctx.currentAbilityId() != null && !ctx.currentAbilityId().isBlank()
                ? ctx.withActivatedAbility(ctx.currentAbilityId())
                : ctx;
        float executeThreshold = ModifierContext.REGISTRY.compute(
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
            FeedChannelPresentationService.cleanup(previous, ctx.store(), ctx.ref());
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
        FeedChannelPresentationService.apply(session, ctx.store(), ctx.ref());
        activeFeeds.put(uuid, session);
        return true;
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        if (uuid != null) {
            activeFeeds.remove(uuid);
            ConsumableMarkerService.clearPlayer(uuid);
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

        if (!VampirismClassifications.isVampiric(uuid)) {
            ConsumableMarkerService.clearPlayer(uuid);
            if (session == null) return;
            activeFeeds.remove(uuid);
            FeedChannelPresentationService.cleanup(session, store, playerRef);
            return;
        }

        ConsumableMarkerService.tick(uuid, playerRef, dt, store);

        if (session == null) return;

        if (!isSessionStillValid(playerRef, session, store)) {
            activeFeeds.remove(uuid);
            FeedChannelPresentationService.cleanup(session, store, playerRef);
            return;
        }

        maintainTargetLock(session, store);

        session.remainingSeconds -= dt;
        if (session.remainingSeconds > 0f) return;

        activeFeeds.remove(uuid);
        commandBuffer.run(bufferStore -> FeedCompletionService.complete(uuid, playerRef, session, bufferStore));
    }

    private static boolean isSessionStillValid(@Nonnull Ref<EntityStore> playerRef,
                                               @Nonnull FeedSession session,
                                               @Nonnull Store<EntityStore> store) {
        EntityStatValue health = FeedEligibility.resolveHealth(session.targetRef, store);
        return health != null
                && health.get() > 0f
                && !FeedEligibility.hasMovedFrom(playerRef, session.casterStartPosition, store, CAST_CANCEL_MOVE_DISTANCE)
                && FeedEligibility.isWithinRange(playerRef, session.targetRef, store, session.maxRange, RANGE_EPSILON);
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

    @Nullable
    private static Ability resolveAbility(@Nullable String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }
        return com.epicseed.vampirism.skill.runtime.VampirismProgressionDefinitionProvider.instance().getAbility(abilityId);
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
                return ModifierContext.REGISTRY.compute(stat, fallback, ctx.modifierContext());
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
        return ModifierContext.REGISTRY.compute(stat, fallback, ctx.modifierContext());
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

}
