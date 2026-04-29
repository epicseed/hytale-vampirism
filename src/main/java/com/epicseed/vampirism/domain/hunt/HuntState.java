package com.epicseed.vampirism.domain.hunt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

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
}
