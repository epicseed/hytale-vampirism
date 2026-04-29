package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.EffectDef;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
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

    private static final Map<UUID, ConversionSession> activeChannels = new ConcurrentHashMap<>();

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
        if (!canConvert(ctx.ref(), ctx.store(), minimumHealth)) {
            return false;
        }
        if (VampireVitalitySystem.getBlood(ctx.ref()) >= VampireVitalitySystem.getMaxBlood(ctx.ref())) {
            return false;
        }

        ConversionSession previous = activeChannels.remove(uuid);
        if (previous != null) {
            cleanupChannelEffects(previous, ctx.store(), ctx.ref());
        }

        ConversionSession session = new ConversionSession(
                ctx.currentAbilityId(),
                durationSeconds,
                tickIntervalSeconds,
                healthCostPerTick,
                bloodGainPerTick,
                minimumHealth,
                lockPosition(ctx.ref(), ctx.store()),
                action.get("casterAnimationId") instanceof String s && !s.isBlank() ? s : null,
                action.get("casterEffectId") instanceof String s && !s.isBlank() ? s : null);
        applyChannelEffects(session, ctx.store(), ctx.ref());
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
        ConversionSession session = activeChannels.get(uuid);
        if (session == null) return;

        if (!VampireStatusRegistry.get().isVampire(uuid) || !isSessionStillValid(playerRef, session, store)) {
            activeChannels.remove(uuid);
            cleanupChannelEffects(session, store, playerRef);
            return;
        }

        session.remainingSeconds -= dt;
        session.tickAccumulator += dt;

        while (session.tickAccumulator + FLOAT_EPSILON >= session.tickIntervalSeconds) {
            session.tickAccumulator -= session.tickIntervalSeconds;
            if (!applyConversionPulse(playerRef, session, store)) {
                activeChannels.remove(uuid);
                cleanupChannelEffects(session, store, playerRef);
                return;
            }
        }

        if (session.remainingSeconds > FLOAT_EPSILON) {
            return;
        }

        activeChannels.remove(uuid);
        cleanupChannelEffects(session, store, playerRef);
    }

    private static boolean isSessionStillValid(@Nonnull Ref<EntityStore> playerRef,
                                               @Nonnull ConversionSession session,
                                               @Nonnull Store<EntityStore> store) {
        return !hasCasterMoved(playerRef, session, store) && canConvert(playerRef, store, session.minimumHealth);
    }

    private static boolean canConvert(@Nonnull Ref<EntityStore> playerRef,
                                      @Nonnull Store<EntityStore> store,
                                      float minimumHealth) {
        EntityStatMap stats = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (stats == null) {
            return false;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.getMax() <= 0f) {
            return false;
        }
        return health.get() > minimumHealth + FLOAT_EPSILON;
    }

    private static boolean applyConversionPulse(@Nonnull Ref<EntityStore> playerRef,
                                                @Nonnull ConversionSession session,
                                                @Nonnull Store<EntityStore> store) {
        EntityStatMap stats = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (stats == null) {
            return false;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return false;
        }

        float currentHealth = health.get();
        float maxDrain = currentHealth - session.minimumHealth;
        if (maxDrain <= FLOAT_EPSILON) {
            return false;
        }

        int currentBlood = VampireVitalitySystem.getBlood(playerRef);
        int maxBlood = VampireVitalitySystem.getMaxBlood(playerRef);
        int missingBlood = Math.max(0, maxBlood - currentBlood);
        if (missingBlood <= 0) {
            return false;
        }

        float ratio = session.bloodGainPerTick / Math.max(FLOAT_EPSILON, session.healthCostPerTick);
        float drain = Math.min(session.healthCostPerTick, maxDrain);
        drain = Math.min(drain, missingBlood / Math.max(FLOAT_EPSILON, ratio));
        if (drain <= FLOAT_EPSILON) {
            return false;
        }

        int bloodGain = Math.min(missingBlood, Math.max(1, Math.round(drain * ratio)));
        if (bloodGain <= 0) {
            return false;
        }

        stats.addStatValue(DefaultEntityStatTypes.getHealth(), -drain);
        VampireVitalitySystem.addBlood(playerRef, bloodGain);
        return true;
    }

    private static boolean hasCasterMoved(@Nonnull Ref<EntityStore> playerRef,
                                          @Nonnull ConversionSession session,
                                          @Nonnull Store<EntityStore> store) {
        if (session.casterStartPosition == null) {
            return false;
        }
        TransformComponent transform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return true;
        }

        Vector3d position = transform.getPosition();
        double dx = position.x - session.casterStartPosition.x;
        double dz = position.z - session.casterStartPosition.z;
        double movedSq = dx * dx + dz * dz;
        return movedSq > CAST_CANCEL_MOVE_DISTANCE * CAST_CANCEL_MOVE_DISTANCE;
    }

    private static void applyChannelEffects(@Nonnull ConversionSession session,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull Ref<EntityStore> playerRef) {
        playCasterAnimation(playerRef, session.casterAnimationId, store);
        applyChannelEffect(playerRef, session.casterEffectId, session.remainingSeconds, store);
    }

    private static void cleanupChannelEffects(@Nonnull ConversionSession session,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Ref<EntityStore> playerRef) {
        stopCasterAnimation(playerRef, session.casterAnimationId, store);
        removeChannelEffect(playerRef, session.casterEffectId, store);
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

    private static void applyChannelEffect(@Nonnull Ref<EntityStore> ref,
                                           @Nullable String effectDefId,
                                           float durationSeconds,
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
        ec.addEffect(ref, effectIndex, effect, Math.max(0.1f, durationSeconds), OverlapBehavior.OVERWRITE, store);
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

    @Nullable
    private static Vector3d lockPosition(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        TransformComponent transform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }

    private static final class ConversionSession {
        private final String abilityId;
        private float remainingSeconds;
        private float tickAccumulator;
        private final float tickIntervalSeconds;
        private final float healthCostPerTick;
        private final float bloodGainPerTick;
        private final float minimumHealth;
        private final Vector3d casterStartPosition;
        private final String casterAnimationId;
        private final String casterEffectId;

        private ConversionSession(@Nullable String abilityId,
                                  float remainingSeconds,
                                  float tickIntervalSeconds,
                                  float healthCostPerTick,
                                  float bloodGainPerTick,
                                  float minimumHealth,
                                  @Nullable Vector3d casterStartPosition,
                                  @Nullable String casterAnimationId,
                                  @Nullable String casterEffectId) {
            this.abilityId = abilityId;
            this.remainingSeconds = remainingSeconds;
            this.tickAccumulator = 0f;
            this.tickIntervalSeconds = tickIntervalSeconds;
            this.healthCostPerTick = healthCostPerTick;
            this.bloodGainPerTick = bloodGainPerTick;
            this.minimumHealth = minimumHealth;
            this.casterStartPosition = casterStartPosition;
            this.casterAnimationId = casterAnimationId;
            this.casterEffectId = casterEffectId;
        }
    }
}
