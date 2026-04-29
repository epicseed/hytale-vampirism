package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nullable;

import com.hypixel.hytale.math.vector.Vector3d;

public final class BloodConversionSession {
    @Nullable public final String abilityId;
    public float remainingSeconds;
    public float tickAccumulator;
    public final float tickIntervalSeconds;
    public final float healthCostPerTick;
    public final float bloodGainPerTick;
    public final float minimumHealth;
    @Nullable public final Vector3d casterStartPosition;
    @Nullable public final String casterAnimationId;
    @Nullable public final String casterEffectId;

    public BloodConversionSession(@Nullable String abilityId,
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
