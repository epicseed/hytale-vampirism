package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.hytale.EntityIdentityAdapter;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntStateMachine {
    public static final String FAIL_PHASE_SUMMONING = "summoning";
    public static final String FAIL_PHASE_PREY_ACTIVE = "prey-active";

    private NightHuntStateMachine() {
    }

    public static void tickIdle(float dt,
                                @Nonnull HuntState state,
                                @Nonnull NightHuntTickContext context,
                                @Nonnull NightHuntRouteScheduler routeScheduler) {
        if (state.cooldownRemainingSeconds > 0f) {
            return;
        }

        state.idleDelayRemainingSeconds -= dt;
        if (state.idleDelayRemainingSeconds > 0f) {
            return;
        }

        if (context.world() == null) {
            state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
            return;
        }

        if (!routeScheduler.startGuidingRoute(
                state,
                context,
                false,
                PlayerSkillRegistry.get().getAcquiredSkillPoints(context.ownerUuid()))) {
            state.cooldownRemainingSeconds = VampirismConfig.get().getNightHuntFailedCooldownSeconds();
            state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
        }
    }

    public static void tickApproaching(float dt,
                                       @Nonnull HuntState state,
                                       @Nonnull NightHuntTickContext context,
                                       @Nonnull NightHuntRouteScheduler routeScheduler) {
        if (state.destination == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        state.approachElapsedSeconds += dt;
        float approachTimeoutSeconds = VampirismConfig.get().getNightHuntApproachTimeoutSeconds();
        if (approachTimeoutSeconds > 0f && state.approachElapsedSeconds >= approachTimeoutSeconds) {
            cancelActiveHunt(context, state, NightHuntMessages.CANCEL_APPROACH_TIMEOUT, "yellow");
            return;
        }

        if (!NightHuntGeometry.isWithinActivationRange(
                context.playerTransform().getPosition(),
                state.destination,
                VampirismConfig.get().getNightHuntArrivalRadius())) {
            return;
        }

        if (context.world() == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        NightHuntMarkerService.clearApproachMarker(state);
        if (!routeScheduler.beginGuidingRoute(
                state,
                context,
                context.playerTransform().getPosition(),
                context.playerTransform().getRotation() != null ? context.playerTransform().getRotation().getYaw() : 0f,
                state.forced,
                state.visualTier)) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
        }
    }

    public static void tickGuiding(float dt,
                                   @Nonnull HuntState state,
                                   @Nonnull NightHuntTickContext context,
                                   @Nonnull NightHuntRouteScheduler routeScheduler) {
        if (state.destination == null) {
            resetToIdle(state, 0f);
            return;
        }

        state.waypointElapsedSeconds += dt;
        float waypointTimeoutSeconds = VampirismConfig.get().getNightHuntWaypointTimeoutSeconds();
        if (waypointTimeoutSeconds > 0f && state.waypointElapsedSeconds >= waypointTimeoutSeconds) {
            cancelActiveHunt(context, state, NightHuntMessages.CANCEL_WAYPOINT_TIMEOUT, "yellow");
            return;
        }
        float waypointCancelDistance = VampirismConfig.get().getNightHuntWaypointCancelDistance();
        if (waypointCancelDistance > 0f
                && NightHuntGeometry.horizontalDistance(context.playerTransform().getPosition(), state.destination) > waypointCancelDistance) {
            cancelActiveHunt(context, state, NightHuntMessages.CANCEL_WAYPOINT_DISTANCE, "yellow");
            return;
        }

        boolean markerActive = NightHuntGeometry.isWithinActivationRange(
                context.playerTransform().getPosition(),
                state.destination,
                VampirismConfig.get().getNightHuntArrivalRadius());
        NightHuntVisualService.syncWaypointDisplay(dt, state, markerActive, context.store(), context.commandBuffer());
        NightHuntVisualService.updateGuideWisps(dt, state, context.store(), context.commandBuffer());
        NightHuntVisualService.emitGuidePulse(
                dt,
                extractPlayerUuid(context.playerRef(), context.store()),
                context.playerTransform(),
                state,
                context.store(),
                context.commandBuffer());
        if (!markerActive) {
            return;
        }

        state.completedWaypoints += 1;
        UUID ownerUuid = extractPlayerUuid(context.playerRef(), context.store());
        if (ownerUuid != null) {
            applyRouteEvent(ownerUuid, context, state);
        }

        if (context.world() != null && state.completedWaypoints < targetWaypointCount(state)) {
            NightHuntCleanupService.clearWaypointDisplay(state, context.commandBuffer());
            NightHuntCleanupService.clearGuideWisps(state, context.commandBuffer());
            if (routeScheduler.queueGuidingRouteResolution(
                    state,
                    context,
                    state.destination,
                    (float) state.routeYawDegrees)) {
                return;
            }
        }

        state.enterSummoning(VampirismConfig.get().getNightHuntSummonDurationSeconds());
        NightHuntMessages.send(context.playerRef(), context.store(), NightHuntMessages.SUMMON, "red");
    }

    public static void tickSummoning(float dt,
                                     @Nonnull HuntState state,
                                     @Nonnull NightHuntTickContext context) {
        if (state.destination == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        boolean markerActive = NightHuntGeometry.isWithinActivationRange(
                context.playerTransform().getPosition(),
                state.destination,
                VampirismConfig.get().getNightHuntSummonCancelRadius());
        NightHuntVisualService.syncWaypointDisplay(dt, state, markerActive, context.store(), context.commandBuffer());
        NightHuntVisualService.updateGuideWisps(dt, state, context.store(), context.commandBuffer());
        NightHuntVisualService.emitGuidePulse(
                dt,
                extractPlayerUuid(context.playerRef(), context.store()),
                context.playerTransform(),
                state,
                context.store(),
                context.commandBuffer());
        if (!markerActive) {
            UUID ownerUuid = extractPlayerUuid(context.playerRef(), context.store());
            if (ownerUuid != null) {
                resolveFailState(ownerUuid, context, FAIL_PHASE_SUMMONING, state);
            } else {
                state.phase = HuntPhase.GUIDING;
                state.summonRemainingSeconds = 0f;
                NightHuntMessages.send(context.playerRef(), context.store(), NightHuntMessages.FAIL, "yellow");
            }
            return;
        }

        state.summonRemainingSeconds -= dt;
        if (state.summonRemainingSeconds > 0f) {
            return;
        }

        context.commandBuffer().run(bufferStore -> spawnMarkedPrey(context.ownerUuid(), context.playerRef(), state, bufferStore));
    }

    public static void tickPreyActive(float dt,
                                      @Nonnull HuntState state,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (state.preyRef == null || state.preyEntityUuid == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntCooldownSeconds());
            return;
        }

        boolean preyDead = isDead(state.preyRef, store);
        state.preyLifetimeRemainingSeconds -= dt;
        if (state.preyLifetimeRemainingSeconds <= 0f || !state.preyRef.isValid() || preyDead) {
            if (preyDead && tryCompleteTrackedPreyKill(state, store)) {
                return;
            }
            if (state.preyRef.isValid()) {
                commandBuffer.tryRemoveEntity(state.preyRef, RemoveReason.REMOVE);
            }
            NightHuntTrackingService.clearPrey(state.preyEntityUuid);
            NightHuntCleanupService.clearHelperRefs(state, commandBuffer);
            if (state.ownerUuid != null && state.ownerPlayerRef != null && state.ownerPlayerRef.isValid()) {
                TransformComponent ownerTransform = (TransformComponent) store.getComponent(
                        state.ownerPlayerRef, TransformComponent.getComponentType());
                if (ownerTransform != null && !preyDead) {
                    resolveFailState(
                            state.ownerUuid,
                            new NightHuntTickContext(
                                    state.ownerUuid,
                                    state.ownerPlayerRef,
                                    ownerTransform,
                                    store,
                                    commandBuffer,
                                    null),
                            FAIL_PHASE_PREY_ACTIVE,
                            state);
                    return;
                }
            }
            resetToIdle(state, VampirismConfig.get().getNightHuntCooldownSeconds());
        }
    }

    public static void resolveStartRoute(@Nonnull UUID ownerUuid,
                                         @Nonnull HuntState state,
                                         long requestId,
                                         @Nonnull Ref<EntityStore> playerRef,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull World world,
                                         @Nonnull Vector3d origin,
                                         float baseYaw,
                                         boolean forced,
                                         int acquiredPoints,
                                         double minDistance,
                                         double maxDistance) {
        HuntState activeState = NightHuntStateStore.get(ownerUuid);
        if (activeState != state || !NightHuntRouteService.isPendingRoute(state, requestId, PendingRouteKind.START)) {
            return;
        }
        if (!playerRef.isValid() || store.isShutdown()) {
            resetToIdle(state, 0f);
            return;
        }
        NightHuntRouteResolution route = NightHuntRouteService.resolveHuntDestinationAllowLoading(
                origin, baseYaw, world, minDistance, maxDistance);
        if (!NightHuntRouteService.isPendingRoute(state, requestId, PendingRouteKind.START)) {
            return;
        }
        if (route == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        NightHuntCleanupService.clearPendingRoute(state);
        state.enterApproaching(
                route.destination(),
                route.yawDegrees(),
                NightHuntVisualService.computeBaseVisualTier(acquiredPoints),
                playerRef,
                forced,
                NightHuntMarkerService.markerIdFor(extractPlayerUuid(playerRef, store)),
                world.getName());
        NightHuntCleanupService.clearHelperRefs(state, null);
        NightHuntMarkerService.setApproachMarker(state, playerRef, store, world, extractPlayerUuid(playerRef, store));
        NightHuntMessages.send(playerRef, store, NightHuntMessages.START, "dark_red");
    }

    public static void resolveApproachRoute(@Nonnull HuntState state,
                                            long requestId,
                                            @Nonnull Ref<EntityStore> playerRef,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull World world,
                                            @Nonnull Vector3d origin,
                                            float baseYaw,
                                            boolean forced,
                                            int visualTier,
                                            double minDistance,
                                            double maxDistance) {
        if (!NightHuntRouteService.isPendingRoute(state, requestId, PendingRouteKind.APPROACH)) {
            return;
        }
        if (!playerRef.isValid() || store.isShutdown()) {
            resetToIdle(state, 0f);
            return;
        }
        NightHuntRouteResolution route = NightHuntRouteService.resolveHuntDestinationAllowLoading(
                origin, baseYaw, world, minDistance, maxDistance);
        if (!NightHuntRouteService.isPendingRoute(state, requestId, PendingRouteKind.APPROACH)) {
            return;
        }
        if (route == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        NightHuntCleanupService.clearPendingRoute(state);
        state.enterGuiding(
                route.destination(),
                route.yawDegrees(),
                NightHuntVisualService.clampVisualTier(visualTier),
                playerRef,
                forced,
                VampirismConfig.get().getNightHuntGuidePulseIntervalSeconds());
        NightHuntCleanupService.clearHelperRefs(state, null);
        NightHuntMessages.send(playerRef, store, NightHuntMessages.TRAIL, "dark_red");
    }

    public static void resolveWaypointRoute(@Nonnull HuntState state,
                                            long requestId,
                                            @Nonnull Ref<EntityStore> playerRef,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull World world,
                                            @Nonnull Vector3d origin,
                                            float baseYaw,
                                            double minDistance,
                                            double maxDistance) {
        if (!NightHuntRouteService.isPendingRoute(state, requestId, PendingRouteKind.WAYPOINT)) {
            return;
        }
        if (!playerRef.isValid() || store.isShutdown()) {
            resetToIdle(state, 0f);
            return;
        }
        NightHuntRouteResolution route = NightHuntRouteService.resolveHuntDestinationAllowLoading(
                origin, baseYaw, world, minDistance, maxDistance);
        if (!NightHuntRouteService.isPendingRoute(state, requestId, PendingRouteKind.WAYPOINT)) {
            return;
        }
        NightHuntCleanupService.clearPendingRoute(state);
        if (route != null) {
            state.advanceGuidingWaypoint(
                    route.destination(),
                    route.yawDegrees(),
                    VampirismConfig.get().getNightHuntGuidePulseIntervalSeconds());
            return;
        }

        state.enterSummoning(VampirismConfig.get().getNightHuntSummonDurationSeconds());
        NightHuntMessages.send(playerRef, store, NightHuntMessages.SUMMON, "red");
    }

    public static void clearTransientHunt(@Nonnull HuntState state, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NightHuntCleanupService.clearTransientHunt(state, commandBuffer, () -> NightHuntMarkerService.clearApproachMarker(state));
    }

    public static void cancelActiveHunt(@Nonnull NightHuntTickContext context,
                                        @Nonnull HuntState state,
                                        @Nonnull String message,
                                        @Nonnull String color) {
        clearTransientHunt(state, context.commandBuffer());
        state.cooldownRemainingSeconds = Math.max(
                state.cooldownRemainingSeconds,
                VampirismConfig.get().getNightHuntFailedCooldownSeconds());
        state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
        NightHuntMessages.send(context.playerRef(), context.store(), message, color);
    }

    public static void completeMarkedPreyKill(@Nonnull UUID ownerUuid,
                                              @Nullable Ref<EntityStore> rewardRef,
                                              @Nonnull HuntState state,
                                              @Nonnull Store<EntityStore> store) {
        int rewardPoints = Math.max(0, state.preyRewardPoints);
        NightHuntTrackingService.clearPrey(state.preyEntityUuid);
        state.phase = HuntPhase.IDLE;
        state.destination = null;
        state.approachElapsedSeconds = 0f;
        state.waypointElapsedSeconds = 0f;
        state.summonRemainingSeconds = 0f;
        state.preyLifetimeRemainingSeconds = 0f;
        state.preyRef = null;
        state.preyEntityUuid = null;
        state.preyRewardPoints = 0;
        state.cooldownRemainingSeconds = VampirismConfig.get().getNightHuntCooldownSeconds();
        state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
        state.guidePulseAccumulator = 0f;
        NightHuntMarkerService.clearApproachMarker(state);
        state.waypointDisplayRef = null;
        state.waypointDisplayActive = false;
        NightHuntCleanupService.clearGuideWisps(state, null);
        NightHuntCleanupService.clearHelperRefs(state, null);
        state.waypointIndex = 0;
        state.completedWaypoints = 0;
        state.bonusWaypoints = 0;
        state.routeYawDegrees = 0f;
        state.waypointRotationDegrees = 0f;
        state.waypointDisplayUpdateAccumulator = 0f;
        state.waypointDisplayTier = 1;
        state.visualTier = 1;
        state.ownerUuid = null;
        state.ownerPlayerRef = null;
        state.forced = false;
        NightHuntCleanupService.clearPendingRoute(state);

        NightHuntRewardService.grantCompletionReward(ownerUuid, rewardRef, store, rewardPoints);
    }

    public static int targetWaypointCount(@Nonnull HuntState state) {
        return Math.max(1, VampirismConfig.get().getNightHuntWaypointCount() + state.bonusWaypoints);
    }

    public static boolean isDead(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        EntityStatMap stats = (EntityStatMap) store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return true;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        return health == null || health.get() <= 0f;
    }

    public static float randomIdleDelaySeconds() {
        VampirismConfig config = VampirismConfig.get();
        return (float) ThreadLocalRandom.current().nextDouble(
                config.getNightHuntIdleDelayMinSeconds(),
                config.getNightHuntIdleDelayMaxSeconds());
    }

    private static void resetToIdle(@Nonnull HuntState state, float cooldownSeconds) {
        NightHuntCleanupService.resetToIdle(state, cooldownSeconds, randomIdleDelaySeconds(), () -> NightHuntMarkerService.clearApproachMarker(state));
    }

    private static void applyRouteEvent(@Nonnull UUID ownerUuid,
                                        @Nonnull NightHuntTickContext context,
                                        @Nonnull HuntState state) {
        NightHuntEventService.applyRouteEvent(
                ownerUuid,
                context.playerRef(),
                context.playerTransform(),
                state,
                context.store(),
                context.commandBuffer(),
                NightHuntTimeService.currentHour(context.store()));
    }

    private static void resolveFailState(@Nonnull UUID ownerUuid,
                                         @Nonnull NightHuntTickContext context,
                                         @Nonnull String failurePhase,
                                         @Nonnull HuntState state) {
        NightHuntFailureService.resolveFailState(
                ownerUuid,
                context.playerRef(),
                context.playerTransform(),
                failurePhase,
                state,
                context.store(),
                context.commandBuffer(),
                NightHuntTimeService.currentHour(context.store()),
                randomIdleDelaySeconds(),
                () -> NightHuntMarkerService.clearApproachMarker(state));
    }

    private static void spawnMarkedPrey(@Nonnull UUID ownerUuid,
                                        @Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull HuntState state,
                                        @Nonnull Store<EntityStore> store) {
        if (!NightHuntPreySpawnService.spawnMarkedPrey(ownerUuid, playerRef, state, store, NightHuntTimeService.currentHour(store))) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
        }
    }

    private static boolean tryCompleteTrackedPreyKill(@Nonnull HuntState state,
                                                      @Nonnull Store<EntityStore> store) {
        if (state.preyEntityUuid == null || state.ownerUuid == null) {
            return false;
        }
        if (!NightHuntTrackingService.hasRecentOwnerHit(state.preyEntityUuid, state.ownerUuid)) {
            return false;
        }
        completeMarkedPreyKill(state.ownerUuid, state.ownerPlayerRef, state, store);
        return true;
    }

    @Nullable
    private static UUID extractPlayerUuid(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return EntityIdentityAdapter.extractPlayerUuid(playerRef, store);
    }
}
