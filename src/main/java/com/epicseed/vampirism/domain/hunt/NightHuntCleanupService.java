package com.epicseed.vampirism.domain.hunt;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntCleanupService {
    private NightHuntCleanupService() {
    }

    public static void clearTransientHunt(@Nonnull HuntState state,
                                          @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                          @Nonnull Runnable clearApproachMarker) {
        clearApproachMarker.run();
        clearGuideWisps(state, commandBuffer);
        clearWaypointDisplay(state, commandBuffer);
        clearHelperRefs(state, commandBuffer);
        if (state.preyRef != null && state.preyRef.isValid()) {
            commandBuffer.tryRemoveEntity(state.preyRef, RemoveReason.REMOVE);
        }
        NightHuntTrackingService.clearPrey(state.preyEntityUuid);
        resetTransientFields(state);
        state.phase = HuntPhase.IDLE;
        state.forced = false;
        clearPendingRoute(state);
    }

    public static void resetToIdle(@Nonnull HuntState state,
                                   float cooldownSeconds,
                                   float idleDelaySeconds,
                                   @Nonnull Runnable clearApproachMarker) {
        NightHuntTrackingService.clearPrey(state.preyEntityUuid);
        clearApproachMarker.run();
        clearHelperRefs(state, null);
        resetTransientFields(state);
        state.phase = HuntPhase.IDLE;
        state.waypointDisplayRef = null;
        state.cooldownRemainingSeconds = Math.max(state.cooldownRemainingSeconds, cooldownSeconds);
        state.idleDelayRemainingSeconds = idleDelaySeconds;
        state.forced = false;
        clearPendingRoute(state);
    }

    public static void clearWaypointDisplay(@Nonnull HuntState state,
                                            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> displayRef = state.waypointDisplayRef;
        state.waypointDisplayRef = null;
        state.waypointDisplayActive = false;
        state.waypointDisplayTier = 1;
        state.waypointDisplayUpdateAccumulator = 0f;
        if (displayRef != null && displayRef.isValid()) {
            commandBuffer.tryRemoveEntity(displayRef, RemoveReason.REMOVE);
        }
    }

    public static void clearGuideWisps(@Nonnull HuntState state,
                                       @Nullable CommandBuffer<EntityStore> commandBuffer) {
        for (GuideWispState guideWisp : state.guideWisps) {
            Ref<EntityStore> guideRef = guideWisp.ref();
            if (guideRef == null || !guideRef.isValid()) {
                continue;
            }
            if (commandBuffer != null) {
                commandBuffer.tryRemoveEntity(guideRef, RemoveReason.REMOVE);
            } else {
                removeEntityImmediately(guideRef);
            }
        }
        state.guideWisps.clear();
    }

    public static void clearHelperRefs(@Nonnull HuntState state,
                                       @Nullable CommandBuffer<EntityStore> commandBuffer) {
        for (Ref<EntityStore> helperRef : state.helperRefs) {
            if (helperRef == null || !helperRef.isValid()) {
                continue;
            }
            if (commandBuffer != null) {
                commandBuffer.tryRemoveEntity(helperRef, RemoveReason.REMOVE);
            } else {
                removeEntityImmediately(helperRef);
            }
        }
        state.helperRefs.clear();
    }

    public static void clearPendingRoute(@Nonnull HuntState state) {
        state.pendingRouteKind = null;
    }

    public static void removeEntityImmediately(@Nullable Ref<EntityStore> ref) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        @SuppressWarnings("unchecked")
        Store<EntityStore> store = (Store<EntityStore>) ref.getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        World world = resolveWorld(store);
        if (world != null) {
            world.execute(() -> {
                if (!store.isShutdown() && ref.isValid()) {
                    store.removeEntity(ref, RemoveReason.REMOVE);
                }
            });
            return;
        }
        if (store.isInThread()) {
            store.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    private static void resetTransientFields(@Nonnull HuntState state) {
        state.destination = null;
        state.summonRemainingSeconds = 0f;
        state.preyLifetimeRemainingSeconds = 0f;
        state.preyRef = null;
        state.preyEntityUuid = null;
        state.preyRewardPoints = 0;
        state.guidePulseAccumulator = 0f;
        state.approachElapsedSeconds = 0f;
        state.waypointElapsedSeconds = 0f;
        state.waypointIndex = 0;
        state.completedWaypoints = 0;
        state.bonusWaypoints = 0;
        state.routeYawDegrees = 0f;
        state.waypointRotationDegrees = 0f;
        state.waypointDisplayUpdateAccumulator = 0f;
        state.waypointDisplayActive = false;
        state.waypointDisplayTier = 1;
        state.visualTier = 1;
        state.ownerUuid = null;
        state.ownerPlayerRef = null;
    }

    @Nullable
    private static World resolveWorld(@Nonnull Store<EntityStore> store) {
        return WorldStoreAdapter.resolveWorld(store);
    }
}
