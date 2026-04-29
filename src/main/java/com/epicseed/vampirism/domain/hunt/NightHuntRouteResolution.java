package com.epicseed.vampirism.domain.hunt;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.vector.Vector3d;

public record NightHuntRouteResolution(@Nonnull Vector3d destination, double yawDegrees) {
}
