package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.hunt.GuideWispState;
import com.epicseed.vampirism.domain.hunt.HuntPhase;
import com.epicseed.vampirism.domain.hunt.HuntState;
import com.epicseed.vampirism.domain.hunt.NightHuntCleanupService;
import com.epicseed.vampirism.domain.hunt.NightHuntDebugInfo;
import com.epicseed.vampirism.domain.hunt.NightHuntMarkerService;
import com.epicseed.vampirism.domain.hunt.NightHuntMessages;
import com.epicseed.vampirism.domain.hunt.NightHuntRouteService;
import com.epicseed.vampirism.domain.hunt.NightHuntRouteScheduler;
import com.epicseed.vampirism.domain.hunt.NightHuntStateMachine;
import com.epicseed.vampirism.domain.hunt.NightHuntStateStore;
import com.epicseed.vampirism.domain.hunt.NightHuntTickContext;
import com.epicseed.vampirism.domain.hunt.NightHuntTimeService;
import com.epicseed.vampirism.domain.hunt.NightHuntTrackingService;
import com.epicseed.vampirism.domain.hunt.NightHuntVisualService;
import com.epicseed.vampirism.domain.hunt.PendingRouteKind;
import com.epicseed.epiccore.hytale.EntityIdentityAdapter;
import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;

public class NightMarkedVictimSystem extends EntityTickingSystem<EntityStore> {

    private static final NightHuntRouteScheduler ROUTE_SCHEDULER = new NightHuntRouteScheduler() {
        @Override
        public boolean startGuidingRoute(@Nonnull HuntState state,
                                         @Nonnull NightHuntTickContext context,
                                         boolean forced,
                                         int acquiredPoints) {
            World world = context.world();
            return world != null && NightMarkedVictimSystem.startGuidingRoute(
                    state,
                    context.ownerUuid(),
                    context.playerRef(),
                    context.playerTransform(),
                    context.store(),
                    world,
                    forced,
                    acquiredPoints);
        }

        @Override
        public boolean beginGuidingRoute(@Nonnull HuntState state,
                                         @Nonnull NightHuntTickContext context,
                                         @Nonnull Vector3d origin,
                                         float baseYaw,
                                         boolean forced,
                                         int visualTier) {
            World world = context.world();
            return world != null && NightMarkedVictimSystem.beginGuidingRoute(
                    state,
                    context.playerRef(),
                    origin,
                    baseYaw,
                    context.store(),
                    world,
                    forced,
                    visualTier);
        }

        @Override
        public boolean queueGuidingRouteResolution(@Nonnull HuntState state,
                                                   @Nonnull NightHuntTickContext context,
                                                   @Nonnull Vector3d origin,
                                                   float baseYaw) {
            World world = context.world();
            return world != null && NightMarkedVictimSystem.queueGuidingRouteResolution(
                    state,
                    context.playerRef(),
                    origin,
                    baseYaw,
                    context.store(),
                    world);
        }
    };
    private final VampirismSkillProgressionAccess progressionAccess;

    public NightMarkedVictimSystem(@Nonnull VampirismSkillProgressionAccess progressionAccess) {
        this.progressionAccess = progressionAccess;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(Player.getComponentType());
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        if (uuid == null) {
            return;
        }
        HuntState state = NightHuntStateStore.remove(uuid);
        if (state == null) {
            return;
        }
        NightHuntTrackingService.clearPrey(state.preyEntityUuid);
        NightHuntMarkerService.clearApproachMarker(state);
        removeEntityImmediately(state.preyRef);
        removeEntityImmediately(state.waypointDisplayRef);
        for (Ref<EntityStore> helperRef : state.helperRefs) {
            removeEntityImmediately(helperRef);
        }
        for (GuideWispState guideWisp : state.guideWisps) {
            removeEntityImmediately(guideWisp.ref());
        }
    }

    public static void onPlayerConnect(@Nullable UUID uuid) {
        if (uuid == null || !VampirismClassifications.isVampiric(uuid)) {
            return;
        }
        NightHuntStateStore.getOrCreate(uuid, NightHuntStateMachine::randomIdleDelaySeconds);
    }

    public static void captureDisconnectState(@Nullable UUID uuid) {
        NightHuntStateStore.captureDisconnectState(uuid, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
    }

    private static void removeEntityImmediately(@Nullable Ref<EntityStore> ref) {
        NightHuntCleanupService.removeEntityImmediately(ref);
    }

    public static boolean resetCooldown(@Nullable UUID uuid) {
        return NightHuntStateStore.resetCooldown(uuid, NightHuntStateMachine::randomIdleDelaySeconds);
    }

    public static boolean forceStart(@Nullable UUID uuid,
                                     @Nullable Ref<EntityStore> playerRef,
                                     @Nullable Store<EntityStore> store,
                                     @Nonnull VampirismSkillProgressionAccess progressionAccess) {
        if (uuid == null || playerRef == null || store == null || !playerRef.isValid()) {
            return false;
        }
        if (!VampirismClassifications.isVampiric(uuid)) {
            return false;
        }

        TransformComponent playerTransform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        World world = resolveWorld(store);
        if (playerTransform == null || world == null) {
            return false;
        }

        HuntState state = NightHuntStateStore.getOrCreate(uuid, NightHuntStateMachine::randomIdleDelaySeconds);
        if (state.phase != HuntPhase.IDLE) {
            return false;
        }

        if (!startGuidingRoute(state, uuid, playerRef, playerTransform, store, world, true,
                progressionAccess.getAcquiredSkillPoints(uuid))) {
            return false;
        }
        state.cooldownRemainingSeconds = 0f;
        state.idleDelayRemainingSeconds = 0f;
        return true;
    }

    @Nonnull
    public static NightHuntDebugInfo getDebugInfo(@Nullable UUID uuid) {
        if (uuid == null) {
            return NightHuntDebugInfo.idle();
        }
        HuntState state = NightHuntStateStore.get(uuid);
        if (state == null) {
            return NightHuntDebugInfo.idle();
        }
        return new NightHuntDebugInfo(
                state.phase.name().toLowerCase().replace('_', '-'),
                state.phase != HuntPhase.IDLE,
                Math.max(0f, state.cooldownRemainingSeconds),
                Math.max(0f, state.idleDelayRemainingSeconds),
                Math.max(0, state.completedWaypoints),
                NightHuntStateMachine.targetWaypointCount(state),
                Math.max(0, state.bonusWaypoints),
                NightHuntVisualService.clampVisualTier(state.visualTier),
                state.forced,
                state.phase == HuntPhase.PREY_ACTIVE && state.preyRef != null && state.preyRef.isValid());
    }

    public static int getBaseVisualTierForAcquiredPoints(int acquiredPoints) {
        return NightHuntVisualService.computeBaseVisualTier(acquiredPoints);
    }

    public static void onPlayerKilledMarkedPrey(@Nullable UUID attackerUuid,
                                                @Nonnull Ref<EntityStore> attackerRef,
                                                @Nonnull Ref<EntityStore> victimRef,
                                                @Nonnull Store<EntityStore> store,
                                                @Nonnull VampirismSkillProgressionAccess progressionAccess) {
        UUID victimUuid = extractEntityUuid(victimRef, store);
        if (victimUuid == null) {
            return;
        }
        UUID ownerUuid = NightHuntTrackingService.ownerOf(victimUuid);
        if (ownerUuid == null) {
            return;
        }

        HuntState state = NightHuntStateStore.getOrCreate(ownerUuid, NightHuntStateMachine::randomIdleDelaySeconds);
        if (attackerUuid != null && attackerUuid.equals(ownerUuid)) {
            NightHuntStateMachine.completeMarkedPreyKill(ownerUuid, attackerRef, state, store, progressionAccess);
        }
    }

    public static void recordMarkedPreyHit(@Nullable UUID attackerUuid,
                                           @Nonnull Ref<EntityStore> victimRef,
                                           @Nonnull Store<EntityStore> store) {
        if (attackerUuid == null) {
            return;
        }
        UUID victimUuid = extractEntityUuid(victimRef, store);
        if (victimUuid == null) {
            return;
        }
        NightHuntTrackingService.recordHit(victimUuid, attackerUuid);
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        TransformComponent playerTransform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerRefComponent == null || playerTransform == null) {
            return;
        }

        UUID uuid = playerRefComponent.getUuid();
        if (!VampirismClassifications.isVampiric(uuid)) {
            HuntState existing = NightHuntStateStore.remove(uuid);
            if (existing != null) {
                NightHuntStateMachine.clearTransientHunt(existing, commandBuffer);
            }
            return;
        }

        HuntState state = NightHuntStateStore.getOrCreate(uuid, NightHuntStateMachine::randomIdleDelaySeconds);
        state.cooldownRemainingSeconds = Math.max(0f, state.cooldownRemainingSeconds - dt);
        World world = resolveWorld(store);
        NightHuntTickContext context = new NightHuntTickContext(uuid, playerRef, playerTransform, store, commandBuffer, world);

        if (state.phase != HuntPhase.IDLE && NightHuntStateMachine.isDead(playerRef, store)) {
            NightHuntStateMachine.cancelActiveHunt(context, state, NightHuntMessages.CANCEL_DEATH, "red");
            return;
        }

        if (!NightHuntTimeService.isNightPeriod(store) && !state.forced) {
            NightHuntStateMachine.clearTransientHunt(state, commandBuffer);
            state.idleDelayRemainingSeconds = NightHuntStateMachine.randomIdleDelaySeconds();
            return;
        }

        switch (state.phase) {
            case IDLE -> NightHuntStateMachine.tickIdle(dt, state, context, ROUTE_SCHEDULER, progressionAccess);
            case ROUTE_PENDING -> {
            }
            case APPROACHING -> NightHuntStateMachine.tickApproaching(dt, state, context, ROUTE_SCHEDULER);
            case GUIDING -> NightHuntStateMachine.tickGuiding(dt, state, context, ROUTE_SCHEDULER, progressionAccess);
            case SUMMONING -> NightHuntStateMachine.tickSummoning(dt, state, context, progressionAccess);
            case PREY_ACTIVE -> NightHuntStateMachine.tickPreyActive(dt, state, store, commandBuffer, progressionAccess);
        }
    }

    private static boolean startGuidingRoute(@Nonnull HuntState state,
                                             @Nonnull UUID ownerUuid,
                                             @Nonnull Ref<EntityStore> playerRef,
                                             @Nonnull TransformComponent playerTransform,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull World world,
                                             boolean forced,
                                             int acquiredPoints) {
        if (state.phase != HuntPhase.IDLE) {
            return false;
        }
        Vector3d origin = playerTransform.getPosition();
        float baseYaw = playerTransform.getRotation() != null ? playerTransform.getRotation().getYaw() : 0f;
        long requestId = NightHuntRouteService.beginPendingRoute(state, PendingRouteKind.START);
        state.ownerPlayerRef = playerRef;
        state.forced = forced;
        VampirismConfig config = VampirismConfig.get();
        world.execute(() -> NightHuntStateMachine.resolveStartRoute(
                ownerUuid,
                state,
                requestId,
                playerRef,
                store,
                world,
                origin,
                baseYaw,
                forced,
                acquiredPoints,
                config.getNightHuntApproachMinDistance(),
                config.getNightHuntApproachMaxDistance()));
        return true;
    }

    private static boolean beginGuidingRoute(@Nonnull HuntState state,
                                             @Nonnull Ref<EntityStore> playerRef,
                                             @Nonnull Vector3d origin,
                                             float baseYaw,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull World world,
                                             boolean forced,
                                             int visualTier) {
        if (state.phase != HuntPhase.APPROACHING) {
            return false;
        }
        long requestId = NightHuntRouteService.beginPendingRoute(state, PendingRouteKind.APPROACH);
        state.ownerPlayerRef = playerRef;
        VampirismConfig config = VampirismConfig.get();
        world.execute(() -> NightHuntStateMachine.resolveApproachRoute(
                state,
                requestId,
                playerRef,
                store,
                world,
                origin,
                baseYaw,
                forced,
                visualTier,
                config.getNightHuntWaypointMinDistance(),
                config.getNightHuntWaypointMaxDistance()));
        return true;
    }

    private static boolean queueGuidingRouteResolution(@Nonnull HuntState state,
                                                       @Nonnull Ref<EntityStore> playerRef,
                                                       @Nonnull Vector3d origin,
                                                       float baseYaw,
                                                       @Nonnull Store<EntityStore> store,
                                                       @Nonnull World world) {
        if (state.phase != HuntPhase.GUIDING) {
            return false;
        }
        long requestId = NightHuntRouteService.beginPendingRoute(state, PendingRouteKind.WAYPOINT);
        VampirismConfig config = VampirismConfig.get();
        world.execute(() -> NightHuntStateMachine.resolveWaypointRoute(
                state,
                requestId,
                playerRef,
                store,
                world,
                origin,
                baseYaw,
                config.getNightHuntWaypointMinDistance(),
                config.getNightHuntWaypointMaxDistance()));
        return true;
    }

    @Nullable
    private static World resolveWorld(@Nonnull Store<EntityStore> store) {
        return WorldStoreAdapter.resolveWorld(store);
    }

    @Nullable
    private static UUID extractEntityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        return EntityIdentityAdapter.extractEntityUuid(ref, store);
    }

}
