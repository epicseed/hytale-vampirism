package com.epicseed.vampirism.domain.hunt;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.config.VampirismConfig;
import com.hypixel.hytale.math.vector.Vector3d;

public final class NightHuntGeometry {
    private NightHuntGeometry() {
    }

    public static double horizontalDistance(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static boolean isWithinActivationRange(@Nonnull Vector3d playerPosition,
                                                  @Nonnull Vector3d destination,
                                                  float horizontalRadius) {
        if (horizontalDistance(playerPosition, destination) > horizontalRadius) {
            return false;
        }
        return Math.abs(playerPosition.y - destination.y) <= VampirismConfig.get().getNightHuntActivationHeightTolerance();
    }
}
