package com.epicseed.vampirism.domain.hunt;

import javax.annotation.Nonnull;

public record NightHuntDebugInfo(@Nonnull String phase,
                                 boolean active,
                                 float cooldownRemainingSeconds,
                                 float idleDelayRemainingSeconds,
                                 int completedWaypoints,
                                 int targetWaypoints,
                                 int bonusWaypoints,
                                 int visualTier,
                                 boolean forced,
                                 boolean preyActive) {
    @Nonnull
    public static NightHuntDebugInfo idle() {
        return new NightHuntDebugInfo("idle", false, 0f, 0f, 0, 1, 0, 1, false, false);
    }
}
