package com.epicseed.vampirism.domain.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Mutable carrier for the night-hunt state machine.
 */
public final class HuntState {
    public HuntPhase phase = HuntPhase.IDLE;
    public float cooldownRemainingSeconds = 0f;
    public float idleDelayRemainingSeconds;
    public float guidePulseAccumulator = 0f;
    public float approachElapsedSeconds = 0f;
    public float waypointElapsedSeconds = 0f;
    public float summonRemainingSeconds = 0f;
    public float preyLifetimeRemainingSeconds = 0f;
    public Vector3d destination;
    public int waypointIndex = 0;
    public int completedWaypoints = 0;
    public int bonusWaypoints = 0;
    public double routeYawDegrees = 0f;
    public int visualTier = 1;
    public float waypointRotationDegrees = 0f;
    public float waypointDisplayUpdateAccumulator = 0f;
    public boolean waypointDisplayActive = false;
    public int waypointDisplayTier = 1;
    public Ref<EntityStore> waypointDisplayRef;
    public String approachMarkerId;
    public String approachMarkerWorldName;
    public final List<GuideWispState> guideWisps = new ArrayList<>();
    public final List<Ref<EntityStore>> helperRefs = new ArrayList<>();
    public Ref<EntityStore> preyRef;
    public UUID preyEntityUuid;
    public UUID ownerUuid;
    public Ref<EntityStore> ownerPlayerRef;
    public int preyRewardPoints = 0;
    public boolean forced = false;
    public long pendingRouteRequestId = 0L;
    public PendingRouteKind pendingRouteKind;

    public HuntState(float idleDelayRemainingSeconds) {
        this.idleDelayRemainingSeconds = idleDelayRemainingSeconds;
    }

    public void enterApproaching(@Nonnull Vector3d destination,
                                 double routeYawDegrees,
                                 int visualTier,
                                 @Nonnull Ref<EntityStore> ownerPlayerRef,
                                 boolean forced,
                                 String approachMarkerId,
                                 String approachMarkerWorldName) {
        this.phase = HuntPhase.APPROACHING;
        this.destination = destination;
        resetRouteProgress(routeYawDegrees, visualTier);
        this.guidePulseAccumulator = 0f;
        this.ownerUuid = null;
        this.ownerPlayerRef = ownerPlayerRef;
        this.approachMarkerId = approachMarkerId;
        this.approachMarkerWorldName = approachMarkerWorldName;
        this.forced = forced;
    }

    public void enterGuiding(@Nonnull Vector3d destination,
                             double routeYawDegrees,
                             int visualTier,
                             @Nonnull Ref<EntityStore> ownerPlayerRef,
                             boolean forced,
                             float guidePulseAccumulator) {
        this.phase = HuntPhase.GUIDING;
        this.destination = destination;
        resetRouteProgress(routeYawDegrees, visualTier);
        this.guidePulseAccumulator = guidePulseAccumulator;
        this.ownerUuid = null;
        this.ownerPlayerRef = ownerPlayerRef;
        this.approachMarkerId = null;
        this.approachMarkerWorldName = null;
        this.forced = forced;
    }

    public void advanceGuidingWaypoint(@Nonnull Vector3d destination,
                                       double routeYawDegrees,
                                       float guidePulseAccumulator) {
        this.phase = HuntPhase.GUIDING;
        this.destination = destination;
        this.routeYawDegrees = routeYawDegrees;
        this.waypointRotationDegrees = (float) routeYawDegrees;
        this.waypointIndex = completedWaypoints + 1;
        this.waypointElapsedSeconds = 0f;
        this.guidePulseAccumulator = guidePulseAccumulator;
    }

    public void enterSummoning(float summonDurationSeconds) {
        this.phase = HuntPhase.SUMMONING;
        this.summonRemainingSeconds = summonDurationSeconds;
    }

    private void resetRouteProgress(double routeYawDegrees, int visualTier) {
        this.waypointIndex = 1;
        this.completedWaypoints = 0;
        this.bonusWaypoints = 0;
        this.routeYawDegrees = routeYawDegrees;
        this.waypointRotationDegrees = (float) routeYawDegrees;
        this.waypointDisplayUpdateAccumulator = 0f;
        this.approachElapsedSeconds = 0f;
        this.waypointElapsedSeconds = 0f;
        this.summonRemainingSeconds = 0f;
        this.preyLifetimeRemainingSeconds = 0f;
        this.preyRewardPoints = 0;
        this.visualTier = visualTier;
    }
}
