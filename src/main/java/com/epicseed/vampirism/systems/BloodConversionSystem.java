package com.epicseed.vampirism.systems;
import com.epicseed.vampirism.modifier.ModifierContext;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.domain.blood.BloodConversionPresentationService;
import com.epicseed.vampirism.domain.blood.BloodConversionPulseService;
import com.epicseed.vampirism.domain.blood.BloodConversionSession;
import com.epicseed.vampirism.domain.blood.FeedEligibility;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BloodConversionSystem extends EntityTickingSystem<EntityStore> {

    private static final float DEFAULT_CHANNEL_DURATION_S = 3.0f;
    private static final float DEFAULT_TICK_INTERVAL_S = 0.5f;
    private static final float DEFAULT_HEALTH_COST = 4.0f;
    private static final float DEFAULT_BLOOD_GAIN = 8.0f;
    private static final float DEFAULT_MINIMUM_HEALTH = 1.0f;
    private static final double CAST_CANCEL_MOVE_DISTANCE = 0.2d;
    private static final float FLOAT_EPSILON = 0.0001f;

    private static final Map<UUID, BloodConversionSession> activeChannels = new ConcurrentHashMap<>();

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
        if (uuid == null) {
            return false;
        }

        Ability ability = resolveAbility(ctx.currentAbilityId());
        float durationSeconds = resolveDurationSeconds(action, ability);
        float tickIntervalSeconds = resolveLiteral(action, "tickIntervalSeconds", DEFAULT_TICK_INTERVAL_S);
        float healthCostPerTick = resolveLiteralOrStat(action, "healthCost", "healthCostStatId", DEFAULT_HEALTH_COST, ctx);
        float bloodGainPerTick = resolveLiteralOrStat(action, "bloodGain", "bloodGainStatId", DEFAULT_BLOOD_GAIN, ctx);
        float minimumHealth = Math.max(0f, resolveLiteral(action, "minimumHealth", DEFAULT_MINIMUM_HEALTH));

        if (durationSeconds <= 0f || tickIntervalSeconds <= 0f || healthCostPerTick <= 0f || bloodGainPerTick <= 0f) {
            return false;
        }
        if (!BloodConversionPulseService.canConvert(ctx.ref(), ctx.store(), minimumHealth)) {
            return false;
        }
        if (VampireVitalitySystem.getBlood(ctx.ref()) >= VampireVitalitySystem.getMaxBlood(ctx.ref())) {
            return false;
        }

        BloodConversionSession previous = activeChannels.remove(uuid);
        if (previous != null) {
            BloodConversionPresentationService.cleanup(previous, ctx.store(), ctx.ref());
        }

        BloodConversionSession session = new BloodConversionSession(
                ctx.currentAbilityId(),
                durationSeconds,
                tickIntervalSeconds,
                healthCostPerTick,
                bloodGainPerTick,
                minimumHealth,
                lockPosition(ctx.ref(), ctx.store()),
                action.get("casterAnimationId") instanceof String s && !s.isBlank() ? s : null,
                action.get("casterEffectId") instanceof String s && !s.isBlank() ? s : null);
        BloodConversionPresentationService.apply(session, ctx.store(), ctx.ref());
        activeChannels.put(uuid, session);
        return true;
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        if (uuid != null) {
            activeChannels.remove(uuid);
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
        BloodConversionSession session = activeChannels.get(uuid);
        if (session == null) return;

        if (!VampireStatusRegistry.get().isVampire(uuid) || !isSessionStillValid(playerRef, session, store)) {
            activeChannels.remove(uuid);
            BloodConversionPresentationService.cleanup(session, store, playerRef);
            return;
        }

        session.remainingSeconds -= dt;
        session.tickAccumulator += dt;

        while (session.tickAccumulator + FLOAT_EPSILON >= session.tickIntervalSeconds) {
            session.tickAccumulator -= session.tickIntervalSeconds;
            if (!BloodConversionPulseService.applyPulse(playerRef, session, store)) {
                activeChannels.remove(uuid);
                BloodConversionPresentationService.cleanup(session, store, playerRef);
                return;
            }
        }

        if (session.remainingSeconds > FLOAT_EPSILON) {
            return;
        }

        activeChannels.remove(uuid);
        BloodConversionPresentationService.cleanup(session, store, playerRef);
    }

    private static boolean isSessionStillValid(@Nonnull Ref<EntityStore> playerRef,
                                               @Nonnull BloodConversionSession session,
                                               @Nonnull Store<EntityStore> store) {
        return !FeedEligibility.hasMovedFrom(playerRef, session.casterStartPosition, store, CAST_CANCEL_MOVE_DISTANCE)
                && BloodConversionPulseService.canConvert(playerRef, store, session.minimumHealth);
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

    @Nullable
    private static Vector3d lockPosition(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        TransformComponent transform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }
}
