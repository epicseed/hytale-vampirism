package com.epicseed.vampirism.domain.hunt;

import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.util.WorldPositionHelper;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;

public final class NightHuntRouteService {
    private NightHuntRouteService() {
    }

    @Nullable
    public static Vector3d findHuntDestination(@Nonnull Vector3d origin,
                                               float baseYaw,
                                               @Nonnull World world,
                                               double minDistance,
                                               double maxDistance) {
        return findHuntDestination(origin, baseYaw, world, minDistance, maxDistance, false);
    }

    @Nullable
    public static Vector3d findHuntDestinationAllowLoading(@Nonnull Vector3d origin,
                                                           float baseYaw,
                                                           @Nonnull World world,
                                                           double minDistance,
                                                           double maxDistance) {
        return findHuntDestination(origin, baseYaw, world, minDistance, maxDistance, true);
    }

    public static long beginPendingRoute(@Nonnull HuntState state, @Nonnull PendingRouteKind kind) {
        state.phase = HuntPhase.ROUTE_PENDING;
        state.pendingRouteKind = kind;
        state.destination = null;
        return ++state.pendingRouteRequestId;
    }

    public static boolean isPendingRoute(@Nonnull HuntState state,
                                         long requestId,
                                         @Nonnull PendingRouteKind kind) {
        return state.phase == HuntPhase.ROUTE_PENDING
                && state.pendingRouteKind == kind
                && state.pendingRouteRequestId == requestId;
    }

    public static double yawBetween(@Nonnull Vector3d from, @Nonnull Vector3d to) {
        return Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x));
    }

    @Nullable
    private static Vector3d findHuntDestination(@Nonnull Vector3d origin,
                                                float baseYaw,
                                                @Nonnull World world,
                                                double minDistance,
                                                double maxDistance,
                                                boolean allowChunkLoading) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (int i = 0; i < 10; i++) {
            double radius = random.nextDouble(minDistance, maxDistance);
            double yawOffset = i < 6
                    ? random.nextDouble(-55.0d, 55.0d)
                    : random.nextDouble(-180.0d, 180.0d);
            double yawRadians = Math.toRadians(baseYaw + yawOffset);
            Vector3d candidate = new Vector3d(
                    origin.x + Math.cos(yawRadians) * radius,
                    origin.y + 1.0d,
                    origin.z + Math.sin(yawRadians) * radius);
            Vector3d safe = allowChunkLoading
                    ? WorldPositionHelper.findSafeGroundPosition(world, candidate)
                    : WorldPositionHelper.findSafeGroundPositionIfLoaded(world, candidate);
            if (safe == null) {
                continue;
            }
            if (horizontalDistance(origin, safe) < minDistance - 1.0d) {
                continue;
            }
            return safe;
        }

        return null;
    }

    private static double horizontalDistance(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
