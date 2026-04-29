package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.modifier.VampireStatType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class FeedSession {
    @Nullable public final String abilityId;
    @Nonnull public final Ref<EntityStore> targetRef;
    public float remainingSeconds;
    public final double maxRange;
    @Nullable public final Vector3d casterStartPosition;
    @Nullable public final Vector3d lockedPosition;
    public final float damage;
    public final int baseBloodGain;
    @Nonnull public final VampireStatType bloodGainStat;
    public final float executeThreshold;
    @Nonnull public final VampireStatType executeThresholdStat;
    @Nullable public final String casterAnimationId;
    @Nullable public final String casterEffectId;
    @Nullable public final String targetEffectId;

    public FeedSession(@Nullable String abilityId,
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
