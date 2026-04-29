package com.epicseed.vampirism.domain.hunt;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.vector.Vector3d;

public interface NightHuntRouteScheduler {
    boolean startGuidingRoute(@Nonnull HuntState state,
                              @Nonnull NightHuntTickContext context,
                              boolean forced,
                              int acquiredPoints);

    boolean beginGuidingRoute(@Nonnull HuntState state,
                              @Nonnull NightHuntTickContext context,
                              @Nonnull Vector3d origin,
                              float baseYaw,
                              boolean forced,
                              int visualTier);

    boolean queueGuidingRouteResolution(@Nonnull HuntState state,
                                        @Nonnull NightHuntTickContext context,
                                        @Nonnull Vector3d origin,
                                        float baseYaw);
}
