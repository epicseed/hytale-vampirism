package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.hunt.GuideWispState;
import com.epicseed.vampirism.domain.hunt.HuntPhase;
import com.epicseed.vampirism.domain.hunt.HuntState;
import com.epicseed.vampirism.domain.hunt.NightHuntCleanupService;
import com.epicseed.vampirism.domain.hunt.NightHuntDebugInfo;
import com.epicseed.vampirism.domain.hunt.NightHuntMarkerService;
import com.epicseed.vampirism.domain.hunt.NightHuntMessages;
import com.epicseed.vampirism.domain.hunt.NightHuntPreySpawnService;
import com.epicseed.vampirism.domain.hunt.NightHuntRewardService;
import com.epicseed.vampirism.domain.hunt.NightHuntRouteService;
import com.epicseed.vampirism.domain.hunt.NightHuntTrackingService;
import com.epicseed.vampirism.domain.hunt.NightHuntVisualService;
import com.epicseed.vampirism.domain.hunt.PendingRouteKind;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class NightMarkedVictimSystem extends EntityTickingSystem<EntityStore> {

    private static final String FAIL_PHASE_SUMMONING = "summoning";
    private static final String FAIL_PHASE_PREY_ACTIVE = "prey-active";
    private static final ConcurrentHashMap<UUID, HuntState> ACTIVE_HUNTS = new ConcurrentHashMap<>();

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
        HuntState state = ACTIVE_HUNTS.remove(uuid);
        if (state == null) {
            return;
        }
        clearPreyTracking(state.preyEntityUuid);
        clearApproachMarker(state);
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
        if (uuid == null || !VampireStatusRegistry.get().isVampire(uuid)) {
            return;
        }
        ACTIVE_HUNTS.computeIfAbsent(uuid, NightMarkedVictimSystem::createRestoredState);
    }

    public static void captureDisconnectState(@Nullable UUID uuid) {
        if (uuid == null) {
            return;
        }
        HuntState state = ACTIVE_HUNTS.get(uuid);
        if (state == null) {
            return;
        }

        if (state.phase == HuntPhase.IDLE) {
            PlayerSkillRegistry.get().setPersistedNightHuntCooldownMs(
                    uuid,
                    secondsToMillis(state.cooldownRemainingSeconds));
        } else {
            PlayerSkillRegistry.get().setPersistedNightHuntCooldownMs(
                    uuid,
                    Math.max(
                            secondsToMillis(state.cooldownRemainingSeconds),
                            secondsToMillis(VampirismConfig.get().getNightHuntFailedCooldownSeconds())));
        }
    }

    private static void removeEntityImmediately(@Nullable Ref<EntityStore> ref) {
        NightHuntCleanupService.removeEntityImmediately(ref);
    }

    public static boolean resetCooldown(@Nullable UUID uuid) {
        if (uuid == null) {
            return false;
        }
        HuntState state = ACTIVE_HUNTS.computeIfAbsent(uuid, NightMarkedVictimSystem::createRestoredState);
        state.cooldownRemainingSeconds = 0f;
        return true;
    }

    public static boolean forceStart(@Nullable UUID uuid,
                                     @Nullable Ref<EntityStore> playerRef,
                                     @Nullable Store<EntityStore> store) {
        if (uuid == null || playerRef == null || store == null || !playerRef.isValid()) {
            return false;
        }
        if (!VampireStatusRegistry.get().isVampire(uuid)) {
            return false;
        }

        TransformComponent playerTransform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        World world = resolveWorld(store);
        if (playerTransform == null || world == null) {
            return false;
        }

        HuntState state = ACTIVE_HUNTS.computeIfAbsent(uuid, NightMarkedVictimSystem::createRestoredState);
        if (state.phase != HuntPhase.IDLE) {
            return false;
        }

        if (!startGuidingRoute(state, uuid, playerRef, playerTransform, store, world, true,
                PlayerSkillRegistry.get().getAcquiredSkillPoints(uuid))) {
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
        HuntState state = ACTIVE_HUNTS.get(uuid);
        if (state == null) {
            return NightHuntDebugInfo.idle();
        }
        return new NightHuntDebugInfo(
                state.phase.name().toLowerCase().replace('_', '-'),
                state.phase != HuntPhase.IDLE,
                Math.max(0f, state.cooldownRemainingSeconds),
                Math.max(0f, state.idleDelayRemainingSeconds),
                Math.max(0, state.completedWaypoints),
                targetWaypointCount(state),
                Math.max(0, state.bonusWaypoints),
                clampVisualTier(state.visualTier),
                state.forced,
                state.phase == HuntPhase.PREY_ACTIVE && state.preyRef != null && state.preyRef.isValid());
    }

    public static int getBaseVisualTierForAcquiredPoints(int acquiredPoints) {
        return computeBaseVisualTier(acquiredPoints);
    }

    public static void onPlayerKilledMarkedPrey(@Nullable UUID attackerUuid,
                                                @Nonnull Ref<EntityStore> attackerRef,
                                                @Nonnull Ref<EntityStore> victimRef,
                                                @Nonnull Store<EntityStore> store) {
        UUID victimUuid = extractEntityUuid(victimRef, store);
        if (victimUuid == null) {
            return;
        }
        UUID ownerUuid = NightHuntTrackingService.ownerOf(victimUuid);
        if (ownerUuid == null) {
            return;
        }

        HuntState state = ACTIVE_HUNTS.computeIfAbsent(ownerUuid, NightMarkedVictimSystem::createRestoredState);
        if (attackerUuid != null && attackerUuid.equals(ownerUuid)) {
            completeMarkedPreyKill(ownerUuid, attackerRef, state, store);
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
        if (!VampireStatusRegistry.get().isVampire(uuid)) {
            HuntState existing = ACTIVE_HUNTS.remove(uuid);
            if (existing != null) {
                clearTransientHunt(existing, commandBuffer);
            }
            return;
        }

        HuntState state = ACTIVE_HUNTS.computeIfAbsent(uuid, NightMarkedVictimSystem::createRestoredState);
        state.cooldownRemainingSeconds = Math.max(0f, state.cooldownRemainingSeconds - dt);

        if (state.phase != HuntPhase.IDLE && isDead(playerRef, store)) {
            cancelActiveHunt(playerRef, state, store, commandBuffer, NightHuntMessages.CANCEL_DEATH, "red");
            return;
        }

        if (!isNightPeriod(store) && !state.forced) {
            clearTransientHunt(state, commandBuffer);
            state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
            return;
        }

        switch (state.phase) {
            case IDLE -> tickIdle(dt, uuid, playerRef, playerTransform, store);
            case ROUTE_PENDING -> {
            }
            case APPROACHING -> tickApproaching(dt, playerRef, playerTransform, state, store, commandBuffer);
            case GUIDING -> tickGuiding(dt, playerRef, playerTransform, state, store, commandBuffer);
            case SUMMONING -> tickSummoning(dt, uuid, playerRef, playerTransform, state, store, commandBuffer);
            case PREY_ACTIVE -> tickPreyActive(dt, state, store, commandBuffer);
        }
    }

    private static void tickIdle(float dt,
                                 @Nonnull UUID uuid,
                                 @Nonnull Ref<EntityStore> playerRef,
                                 @Nonnull TransformComponent playerTransform,
                                 @Nonnull Store<EntityStore> store) {
        HuntState state = ACTIVE_HUNTS.get(uuid);
        if (state == null || state.cooldownRemainingSeconds > 0f) {
            return;
        }

        state.idleDelayRemainingSeconds -= dt;
        if (state.idleDelayRemainingSeconds > 0f) {
            return;
        }

        World world = resolveWorld(store);
        if (world == null) {
            state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
            return;
        }

        if (!startGuidingRoute(state, uuid, playerRef, playerTransform, store, world, false,
                PlayerSkillRegistry.get().getAcquiredSkillPoints(uuid))) {
            state.cooldownRemainingSeconds = VampirismConfig.get().getNightHuntFailedCooldownSeconds();
            state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
            return;
        }
    }

    private static void tickApproaching(float dt,
                                        @Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull TransformComponent playerTransform,
                                        @Nonnull HuntState state,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (state.destination == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        state.approachElapsedSeconds += dt;
        float approachTimeoutSeconds = VampirismConfig.get().getNightHuntApproachTimeoutSeconds();
        if (approachTimeoutSeconds > 0f && state.approachElapsedSeconds >= approachTimeoutSeconds) {
            cancelActiveHunt(playerRef, state, store, commandBuffer, NightHuntMessages.CANCEL_APPROACH_TIMEOUT, "yellow");
            return;
        }

        if (!isWithinActivationRange(
                playerTransform.getPosition(),
                state.destination,
                VampirismConfig.get().getNightHuntArrivalRadius())) {
            return;
        }

        World world = resolveWorld(store);
        if (world == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        clearApproachMarker(state);
        if (!beginGuidingRoute(
                state,
                playerRef,
                playerTransform.getPosition(),
                playerTransform.getRotation() != null ? playerTransform.getRotation().getYaw() : 0f,
                store,
                world,
                state.forced,
                state.visualTier)) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }
    }

    private static void tickGuiding(float dt,
                                    @Nonnull Ref<EntityStore> playerRef,
                                    @Nonnull TransformComponent playerTransform,
                                    @Nonnull HuntState state,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (state.destination == null) {
            resetToIdle(state, 0f);
            return;
        }

        state.waypointElapsedSeconds += dt;
        float waypointTimeoutSeconds = VampirismConfig.get().getNightHuntWaypointTimeoutSeconds();
        if (waypointTimeoutSeconds > 0f && state.waypointElapsedSeconds >= waypointTimeoutSeconds) {
            cancelActiveHunt(playerRef, state, store, commandBuffer, NightHuntMessages.CANCEL_WAYPOINT_TIMEOUT, "yellow");
            return;
        }
        float waypointCancelDistance = VampirismConfig.get().getNightHuntWaypointCancelDistance();
        if (waypointCancelDistance > 0f
                && horizontalDistance(playerTransform.getPosition(), state.destination) > waypointCancelDistance) {
            cancelActiveHunt(playerRef, state, store, commandBuffer, NightHuntMessages.CANCEL_WAYPOINT_DISTANCE, "yellow");
            return;
        }

        boolean markerActive = isWithinActivationRange(
                playerTransform.getPosition(),
                state.destination,
                VampirismConfig.get().getNightHuntArrivalRadius());
        syncWaypointDisplay(dt, state, markerActive, store, commandBuffer);
        updateGuideWisps(dt, state, store, commandBuffer);
        emitGuidePulse(dt, playerRef, playerTransform, state, store, commandBuffer);
        if (!markerActive) {
            return;
        }

        state.completedWaypoints += 1;
        UUID ownerUuid = extractPlayerUuid(playerRef, store);
        if (ownerUuid != null) {
            applyRouteEvent(ownerUuid, playerRef, playerTransform, state, store, commandBuffer);
        }

        World world = resolveWorld(store);
        if (world != null && state.completedWaypoints < targetWaypointCount(state)) {
            clearWaypointDisplay(state, commandBuffer);
            clearGuideWisps(state, commandBuffer);
            if (queueGuidingRouteResolution(
                    state,
                    playerRef,
                    state.destination,
                    (float) state.routeYawDegrees,
                    store,
                    world)) {
                return;
            }
        }

        state.phase = HuntPhase.SUMMONING;
        state.summonRemainingSeconds = VampirismConfig.get().getNightHuntSummonDurationSeconds();
        sendPlayerMessage(playerRef, store, NightHuntMessages.SUMMON, "red");
    }

    private static void tickSummoning(float dt,
                                      @Nonnull UUID uuid,
                                      @Nonnull Ref<EntityStore> playerRef,
                                      @Nonnull TransformComponent playerTransform,
                                      @Nonnull HuntState state,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (state.destination == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        boolean markerActive = isWithinActivationRange(
                playerTransform.getPosition(),
                state.destination,
                VampirismConfig.get().getNightHuntSummonCancelRadius());
        syncWaypointDisplay(dt, state, markerActive, store, commandBuffer);
        updateGuideWisps(dt, state, store, commandBuffer);
        emitGuidePulse(dt, playerRef, playerTransform, state, store, commandBuffer);
        if (!markerActive) {
            UUID ownerUuid = extractPlayerUuid(playerRef, store);
            if (ownerUuid != null) {
                resolveFailState(ownerUuid, playerRef, playerTransform, FAIL_PHASE_SUMMONING, state, store, commandBuffer);
            } else {
                state.phase = HuntPhase.GUIDING;
                state.summonRemainingSeconds = 0f;
                sendPlayerMessage(playerRef, store, NightHuntMessages.FAIL, "yellow");
            }
            return;
        }

        state.summonRemainingSeconds -= dt;
        if (state.summonRemainingSeconds > 0f) {
            return;
        }

        commandBuffer.run(bufferStore -> spawnMarkedPrey(uuid, playerRef, state, bufferStore));
    }

    private static void tickPreyActive(float dt,
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
            clearPreyTracking(state.preyEntityUuid);
            clearHelperRefs(state, commandBuffer);
            if (state.ownerUuid != null && state.ownerPlayerRef != null && state.ownerPlayerRef.isValid()) {
                TransformComponent ownerTransform = (TransformComponent) store.getComponent(
                        state.ownerPlayerRef, TransformComponent.getComponentType());
                if (ownerTransform != null && !preyDead) {
                    resolveFailState(state.ownerUuid, state.ownerPlayerRef, ownerTransform,
                            FAIL_PHASE_PREY_ACTIVE, state, store, commandBuffer);
                    return;
                }
            }
            resetToIdle(state, VampirismConfig.get().getNightHuntCooldownSeconds());
        }
    }

    private static void emitGuidePulse(float dt,
                                       @Nonnull Ref<EntityStore> playerRef,
                                       @Nonnull TransformComponent playerTransform,
                                       @Nonnull HuntState state,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NightHuntVisualService.emitGuidePulse(dt, extractPlayerUuid(playerRef, store), playerTransform, state, store, commandBuffer);
    }

    private static void spawnGuidePulse(@Nonnull UUID ownerUuid,
                                        @Nonnull TransformComponent playerTransform,
                                        @Nonnull HuntState state,
                                         int pulseIndex,
                                         int pulseCount,
                                         @Nonnull Store<EntityStore> store) {
        NightHuntVisualService.spawnGuidePulse(ownerUuid, playerTransform, state, pulseIndex, pulseCount, store);
    }

    private static void updateGuideWisps(float dt,
                                          @Nonnull HuntState state,
                                          @Nonnull Store<EntityStore> store,
                                          @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NightHuntVisualService.updateGuideWisps(dt, state, store, commandBuffer);
    }

    private static void syncWaypointDisplay(float dt,
                                            @Nonnull HuntState state,
                                             boolean markerActive,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NightHuntVisualService.syncWaypointDisplay(dt, state, markerActive, store, commandBuffer);
    }

    private static void spawnWaypointDisplay(@Nonnull HuntState state,
                                              boolean markerActive,
                                              @Nonnull Store<EntityStore> store) {
        NightHuntVisualService.spawnWaypointDisplay(state, markerActive, store);
    }

    private static void spawnMarkedPrey(@Nonnull UUID ownerUuid,
                                        @Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull HuntState state,
                                        @Nonnull Store<EntityStore> store) {
        if (!NightHuntPreySpawnService.spawnMarkedPrey(ownerUuid, playerRef, state, store, currentHour(store))) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
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
        long requestId = beginPendingRoute(state, PendingRouteKind.START);
        state.ownerPlayerRef = playerRef;
        state.forced = forced;
        VampirismConfig config = VampirismConfig.get();
        world.execute(() -> resolveStartGuidingRoute(
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

    private static void resolveStartGuidingRoute(@Nonnull UUID ownerUuid,
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
        HuntState activeState = ACTIVE_HUNTS.get(ownerUuid);
        if (activeState != state || !isPendingRoute(state, requestId, PendingRouteKind.START)) {
            return;
        }
        if (!playerRef.isValid() || store.isShutdown()) {
            resetToIdle(state, 0f);
            return;
        }
        Vector3d destination = findHuntDestinationAllowLoading(origin, baseYaw, world, minDistance, maxDistance);
        if (!isPendingRoute(state, requestId, PendingRouteKind.START)) {
            return;
        }
        if (destination == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        clearPendingRoute(state);
        state.phase = HuntPhase.APPROACHING;
        state.destination = destination;
        state.waypointIndex = 1;
        state.completedWaypoints = 0;
        state.bonusWaypoints = 0;
        state.routeYawDegrees = yawBetween(origin, destination);
        state.waypointRotationDegrees = (float) state.routeYawDegrees;
        state.waypointDisplayUpdateAccumulator = 0f;
        state.approachElapsedSeconds = 0f;
        state.waypointElapsedSeconds = 0f;
        state.guidePulseAccumulator = 0f;
        state.summonRemainingSeconds = 0f;
        state.preyLifetimeRemainingSeconds = 0f;
        state.preyRewardPoints = 0;
        state.visualTier = computeBaseVisualTier(acquiredPoints);
        state.ownerUuid = null;
        state.ownerPlayerRef = playerRef;
        state.approachMarkerId = NightHuntMarkerService.markerIdFor(extractPlayerUuid(playerRef, store));
        state.approachMarkerWorldName = world.getName();
        clearHelperRefs(state, null);
        state.forced = forced;
        setApproachMarker(state, playerRef, store, world);
        sendPlayerMessage(playerRef, store, NightHuntMessages.START, "dark_red");
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
        long requestId = beginPendingRoute(state, PendingRouteKind.APPROACH);
        state.ownerPlayerRef = playerRef;
        VampirismConfig config = VampirismConfig.get();
        world.execute(() -> resolveApproachGuidingRoute(
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

    private static void resolveApproachGuidingRoute(@Nonnull HuntState state,
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
        if (!isPendingRoute(state, requestId, PendingRouteKind.APPROACH)) {
            return;
        }
        if (!playerRef.isValid() || store.isShutdown()) {
            resetToIdle(state, 0f);
            return;
        }
        Vector3d destination = findHuntDestinationAllowLoading(origin, baseYaw, world, minDistance, maxDistance);
        if (!isPendingRoute(state, requestId, PendingRouteKind.APPROACH)) {
            return;
        }
        if (destination == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        clearPendingRoute(state);
        state.phase = HuntPhase.GUIDING;
        state.destination = destination;
        state.waypointIndex = 1;
        state.completedWaypoints = 0;
        state.bonusWaypoints = 0;
        state.routeYawDegrees = yawBetween(origin, destination);
        state.waypointRotationDegrees = (float) state.routeYawDegrees;
        state.waypointDisplayUpdateAccumulator = 0f;
        state.approachElapsedSeconds = 0f;
        state.waypointElapsedSeconds = 0f;
        state.guidePulseAccumulator = VampirismConfig.get().getNightHuntGuidePulseIntervalSeconds();
        state.summonRemainingSeconds = 0f;
        state.preyLifetimeRemainingSeconds = 0f;
        state.preyRewardPoints = 0;
        state.visualTier = clampVisualTier(visualTier);
        state.ownerUuid = null;
        state.approachMarkerId = null;
        state.approachMarkerWorldName = null;
        clearHelperRefs(state, null);
        state.forced = forced;
        sendPlayerMessage(playerRef, store, NightHuntMessages.TRAIL, "dark_red");
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
        long requestId = beginPendingRoute(state, PendingRouteKind.WAYPOINT);
        VampirismConfig config = VampirismConfig.get();
        world.execute(() -> resolveWaypointGuidingRoute(
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

    private static void resolveWaypointGuidingRoute(@Nonnull HuntState state,
                                                    long requestId,
                                                    @Nonnull Ref<EntityStore> playerRef,
                                                    @Nonnull Store<EntityStore> store,
                                                    @Nonnull World world,
                                                    @Nonnull Vector3d origin,
                                                    float baseYaw,
                                                    double minDistance,
                                                    double maxDistance) {
        if (!isPendingRoute(state, requestId, PendingRouteKind.WAYPOINT)) {
            return;
        }
        if (!playerRef.isValid() || store.isShutdown()) {
            resetToIdle(state, 0f);
            return;
        }
        Vector3d nextDestination = findHuntDestinationAllowLoading(origin, baseYaw, world, minDistance, maxDistance);
        if (!isPendingRoute(state, requestId, PendingRouteKind.WAYPOINT)) {
            return;
        }
        clearPendingRoute(state);
        if (nextDestination != null) {
            state.phase = HuntPhase.GUIDING;
            state.routeYawDegrees = yawBetween(origin, nextDestination);
            state.destination = nextDestination;
            state.waypointIndex = state.completedWaypoints + 1;
            state.waypointElapsedSeconds = 0f;
            state.guidePulseAccumulator = VampirismConfig.get().getNightHuntGuidePulseIntervalSeconds();
            return;
        }

        state.phase = HuntPhase.SUMMONING;
        state.summonRemainingSeconds = VampirismConfig.get().getNightHuntSummonDurationSeconds();
        sendPlayerMessage(playerRef, store, NightHuntMessages.SUMMON, "red");
    }

    @Nullable
    private static Vector3d findHuntDestination(@Nonnull Vector3d origin,
                                                float baseYaw,
                                                @Nonnull World world,
                                                double minDistance,
                                                double maxDistance) {
        return NightHuntRouteService.findHuntDestination(origin, baseYaw, world, minDistance, maxDistance);
    }

    @Nullable
    private static Vector3d findHuntDestinationAllowLoading(@Nonnull Vector3d origin,
                                                            float baseYaw,
                                                            @Nonnull World world,
                                                            double minDistance,
                                                            double maxDistance) {
        return NightHuntRouteService.findHuntDestinationAllowLoading(origin, baseYaw, world, minDistance, maxDistance);
    }

    private static void clearTransientHunt(@Nonnull HuntState state, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NightHuntCleanupService.clearTransientHunt(state, commandBuffer, () -> clearApproachMarker(state));
    }

    private static void resetToIdle(@Nonnull HuntState state, float cooldownSeconds) {
        NightHuntCleanupService.resetToIdle(state, cooldownSeconds, randomIdleDelaySeconds(), () -> clearApproachMarker(state));
    }

    private static void clearWaypointDisplay(@Nonnull HuntState state, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NightHuntCleanupService.clearWaypointDisplay(state, commandBuffer);
    }

    private static void clearGuideWisps(@Nonnull HuntState state, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        NightHuntCleanupService.clearGuideWisps(state, commandBuffer);
    }

    private static void cancelActiveHunt(@Nonnull Ref<EntityStore> playerRef,
                                         @Nonnull HuntState state,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                         @Nonnull String message,
                                         @Nonnull String color) {
        clearTransientHunt(state, commandBuffer);
        state.cooldownRemainingSeconds = Math.max(
                state.cooldownRemainingSeconds,
                VampirismConfig.get().getNightHuntFailedCooldownSeconds());
        state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
        sendPlayerMessage(playerRef, store, message, color);
    }

    private static void clearHelperRefs(@Nonnull HuntState state, @Nullable CommandBuffer<EntityStore> commandBuffer) {
        NightHuntCleanupService.clearHelperRefs(state, commandBuffer);
    }

    private static void applyRouteEvent(@Nonnull UUID ownerUuid,
                                        @Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull TransformComponent playerTransform,
                                        @Nonnull HuntState state,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NightHuntSpawnRegistry.RouteEventOption routeEvent = NightHuntSpawnRegistry.get().pickRouteEvent(
                new NightHuntSpawnRegistry.RouteEventContext(
                        PlayerSkillRegistry.get().getAcquiredSkillPoints(ownerUuid),
                        state.completedWaypoints,
                        state.forced,
                        currentHour(store),
                        state.visualTier));
        if (routeEvent == null) {
            return;
        }

        if (routeEvent.text() != null) {
            sendPlayerMessage(playerRef, store, routeEvent.text(), routeEvent.textColor());
        }
        state.bonusWaypoints = Math.max(0, state.bonusWaypoints + routeEvent.extraWaypoints());
        state.visualTier = clampVisualTier(state.visualTier + routeEvent.visualTierDelta());
        for (int i = 0; i < routeEvent.instantGuideBursts(); i++) {
            final int burstIndex = i;
            commandBuffer.run(bufferStore -> spawnGuidePulse(
                    ownerUuid,
                    playerTransform,
                    state,
                    burstIndex,
                    Math.max(1, routeEvent.instantGuideBursts()),
                    bufferStore));
        }
    }

    private static void resolveFailState(@Nonnull UUID ownerUuid,
                                         @Nonnull Ref<EntityStore> playerRef,
                                         @Nonnull TransformComponent playerTransform,
                                         @Nonnull String failurePhase,
                                         @Nonnull HuntState state,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        NightHuntSpawnRegistry.FailStateOption failState = NightHuntSpawnRegistry.get().pickFailState(
                new NightHuntSpawnRegistry.FailStateContext(
                        PlayerSkillRegistry.get().getAcquiredSkillPoints(ownerUuid),
                        state.completedWaypoints,
                        state.forced,
                        currentHour(store),
                        state.visualTier,
                        failurePhase));
        if (failState == null) {
            if (FAIL_PHASE_SUMMONING.equals(failurePhase)) {
                state.phase = HuntPhase.GUIDING;
                state.summonRemainingSeconds = 0f;
                sendPlayerMessage(playerRef, store, NightHuntMessages.FAIL, "yellow");
                return;
            }
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        if (failState.text() != null) {
            sendPlayerMessage(playerRef, store, failState.text(), failState.textColor());
        }

        if (failState.resumeGuiding()) {
            state.phase = HuntPhase.GUIDING;
            state.summonRemainingSeconds = 0f;
            state.visualTier = clampVisualTier(Math.max(state.visualTier, failState.ambushVisualTier()));
            clearGuideWisps(state, commandBuffer);
            return;
        }

        if (failState.ambushRoleId() != null) {
            clearGuideWisps(state, commandBuffer);
            clearWaypointDisplay(state, commandBuffer);
            clearHelperRefs(state, commandBuffer);
            if (state.preyRef != null && state.preyRef.isValid()) {
                commandBuffer.tryRemoveEntity(state.preyRef, RemoveReason.REMOVE);
            }
            clearPreyTracking(state.preyEntityUuid);
            commandBuffer.run(bufferStore -> spawnAmbushPrey(ownerUuid, playerRef, playerTransform, state, failState, bufferStore));
            return;
        }

        resetToIdle(state, failState.cooldownSeconds() > 0f
                ? failState.cooldownSeconds()
                : VampirismConfig.get().getNightHuntFailedCooldownSeconds());
    }

    private static void spawnAmbushPrey(@Nonnull UUID ownerUuid,
                                        @Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull TransformComponent playerTransform,
                                        @Nonnull HuntState state,
                                        @Nonnull NightHuntSpawnRegistry.FailStateOption failState,
                                        @Nonnull Store<EntityStore> store) {
        if (!NightHuntPreySpawnService.spawnAmbushPrey(
                ownerUuid,
                playerRef,
                playerTransform,
                state,
                failState,
                store,
                resolveWorld(store))) {
            resetToIdle(state, failState.cooldownSeconds() > 0f
                    ? failState.cooldownSeconds()
                    : VampirismConfig.get().getNightHuntFailedCooldownSeconds());
        }
    }

    private static int targetWaypointCount(@Nonnull HuntState state) {
        return Math.max(1, VampirismConfig.get().getNightHuntWaypointCount() + state.bonusWaypoints);
    }

    private static int computeBaseVisualTier(int acquiredPoints) {
        return NightHuntVisualService.computeBaseVisualTier(acquiredPoints);
    }

    private static int clampVisualTier(int visualTier) {
        return NightHuntVisualService.clampVisualTier(visualTier);
    }

    private static int currentHour(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        return worldTime != null ? worldTime.getCurrentHour() : -1;
    }

    @Nonnull
    private static HuntState createRestoredState(@Nonnull UUID uuid) {
        HuntState state = new HuntState(randomIdleDelaySeconds());
        long cooldownMs = PlayerSkillRegistry.get().getPersistedNightHuntCooldownMs(uuid);
        if (cooldownMs >= 0L) {
            state.cooldownRemainingSeconds = millisToSeconds(cooldownMs);
        }
        return state;
    }

    private static long secondsToMillis(float seconds) {
        if (seconds <= 0f) {
            return 0L;
        }
        return (long) Math.ceil(seconds * 1000.0d);
    }

    private static float millisToSeconds(long millis) {
        if (millis <= 0L) {
            return 0f;
        }
        return millis / 1000f;
    }

    private static boolean isNightPeriod(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        if (worldTime == null) {
            return false;
        }
        VampirismConfig config = VampirismConfig.get();
        return worldTime.getSunlightFactor() < 0.01d
                && !worldTime.isDayTimeWithinRange(config.getDayStartHour(), config.getNightStartHour());
    }

    @Nullable
    private static World resolveWorld(@Nonnull Store<EntityStore> store) {
        EntityStore entityStore = store.getExternalData();
        return entityStore != null ? entityStore.getWorld() : null;
    }

    private static void setApproachMarker(@Nonnull HuntState state,
                                           @Nonnull Ref<EntityStore> playerRef,
                                           @Nonnull Store<EntityStore> store,
                                           @Nonnull World world) {
        NightHuntMarkerService.setApproachMarker(state, playerRef, store, world, extractPlayerUuid(playerRef, store));
    }

    private static void clearApproachMarker(@Nonnull HuntState state) {
        NightHuntMarkerService.clearApproachMarker(state);
    }

    @Nullable
    private static UUID extractPlayerUuid(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        return playerRefComponent != null ? playerRefComponent.getUuid() : null;
    }

    @Nullable
    private static UUID extractEntityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComponent = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }

    private static void clearPreyTracking(@Nullable UUID preyUuid) {
        NightHuntTrackingService.clearPrey(preyUuid);
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

    private static void completeMarkedPreyKill(@Nonnull UUID ownerUuid,
                                               @Nullable Ref<EntityStore> rewardRef,
                                               @Nonnull HuntState state,
                                               @Nonnull Store<EntityStore> store) {
        int rewardPoints = Math.max(0, state.preyRewardPoints);
        clearPreyTracking(state.preyEntityUuid);
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
        clearApproachMarker(state);
        state.waypointDisplayRef = null;
        state.waypointDisplayActive = false;
        clearGuideWisps(state, null);
        clearHelperRefs(state, null);
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
        clearPendingRoute(state);

        NightHuntRewardService.grantCompletionReward(ownerUuid, rewardRef, store, rewardPoints);
    }

    private static boolean isDead(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        EntityStatMap stats = (EntityStatMap) store.getComponent(ref, EntityStatMap.getComponentType());
        if (stats == null) {
            return true;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        return health == null || health.get() <= 0f;
    }

    private static void sendPlayerMessage(@Nonnull Ref<EntityStore> playerRef,
                                          @Nonnull Store<EntityStore> store,
                                          @Nonnull String text,
                                          @Nonnull String color) {
        NightHuntMessages.send(playerRef, store, text, color);
    }

    private static double horizontalDistance(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double distance(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static boolean isWithinActivationRange(@Nonnull Vector3d playerPosition,
                                                   @Nonnull Vector3d destination,
                                                   float horizontalRadius) {
        if (horizontalDistance(playerPosition, destination) > horizontalRadius) {
            return false;
        }
        return Math.abs(playerPosition.y - destination.y) <= VampirismConfig.get().getNightHuntActivationHeightTolerance();
    }

    private static float randomIdleDelaySeconds() {
        VampirismConfig config = VampirismConfig.get();
        return (float) ThreadLocalRandom.current().nextDouble(
                config.getNightHuntIdleDelayMinSeconds(),
                config.getNightHuntIdleDelayMaxSeconds());
    }

    private static double yawBetween(@Nonnull Vector3d from, @Nonnull Vector3d to) {
        return NightHuntRouteService.yawBetween(from, to);
    }

    private static long beginPendingRoute(@Nonnull HuntState state, @Nonnull PendingRouteKind kind) {
        return NightHuntRouteService.beginPendingRoute(state, kind);
    }

    private static void clearPendingRoute(@Nonnull HuntState state) {
        NightHuntCleanupService.clearPendingRoute(state);
    }

    private static boolean isPendingRoute(@Nonnull HuntState state,
                                           long requestId,
                                           @Nonnull PendingRouteKind kind) {
        return NightHuntRouteService.isPendingRoute(state, requestId, kind);
    }

}
