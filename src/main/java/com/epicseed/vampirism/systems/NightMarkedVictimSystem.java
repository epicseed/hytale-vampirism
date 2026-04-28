package com.epicseed.vampirism.systems;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.util.WorldPositionHelper;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.entities.player.data.PlayerWorldData;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.markers.user.UserMapMarker;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class NightMarkedVictimSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String GUIDE_PROJECTILE_ID = "Vampirism_NightHunt_Wisp";
    private static final String WAYPOINT_PROJECTILE_ID = "Vampirism_NightHunt_Waypoint";
    private static final String WAYPOINT_PROJECTILE_ACTIVE_ID = "Vampirism_NightHunt_Waypoint_Active";
    private static final String WAYPOINT_PROJECTILE_TIER2_ID = "Vampirism_NightHunt_Waypoint_T2";
    private static final String WAYPOINT_PROJECTILE_ACTIVE_TIER2_ID = "Vampirism_NightHunt_Waypoint_Active_T2";
    private static final String WAYPOINT_PROJECTILE_TIER3_ID = "Vampirism_NightHunt_Waypoint_T3";
    private static final String WAYPOINT_PROJECTILE_ACTIVE_TIER3_ID = "Vampirism_NightHunt_Waypoint_Active_T3";
    private static final String HUNT_START_TEXT = "A blood omen marks a distant place on your map.";
    private static final String HUNT_TRAIL_TEXT = "The blood trail awakens from the marked place.";
    private static final String HUNT_SUMMON_TEXT = "The marked prey draws near...";
    private static final String HUNT_SPAWN_TEXT = "The marked prey emerged from the blood's call.";
    private static final String HUNT_FAIL_TEXT = "The trail faded before the summoning could complete.";
    private static final String HUNT_CANCEL_DEATH_TEXT = "Your death snuffs out the blood hunt.";
    private static final String HUNT_CANCEL_APPROACH_TIMEOUT_TEXT = "The blood omen faded before you could reach it.";
    private static final String HUNT_CANCEL_WAYPOINT_TIMEOUT_TEXT = "The blood trail went cold after too much delay.";
    private static final String HUNT_CANCEL_WAYPOINT_DISTANCE_TEXT = "You strayed too far from the blood trail, and the hunt collapsed.";
    private static final String FAIL_PHASE_SUMMONING = "summoning";
    private static final String FAIL_PHASE_PREY_ACTIVE = "prey-active";
    private static final String PREY_HEALTH_MODIFIER_KEY = "night_hunt_prey_health_bonus";
    private static final String APPROACH_MARKER_ID_PREFIX = "vampirism-night-hunt-";
    private static final String APPROACH_MARKER_ICON = "VampireFang.png";
    private static final String APPROACH_MARKER_NAME = "Blood Omen";
    private static final double WAYPOINT_TERMINAL_VELOCITY = 0.001d;
    private static final double GUIDE_WISP_MIN_TURN_BLEND = 0.10d;
    private static final double GUIDE_WISP_MAX_TURN_BLEND = 0.24d;
    private static final double GUIDE_WISP_TURN_BLEND_PER_SECOND = 5.5d;
    private static final long PREY_HIT_CREDIT_WINDOW_MS = 5000L;

    private static final ConcurrentHashMap<UUID, HuntState> ACTIVE_HUNTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, UUID> PREY_OWNERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PreyHitRecord> PREY_LAST_HITS = new ConcurrentHashMap<>();

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
    public static HuntDebugInfo getDebugInfo(@Nullable UUID uuid) {
        if (uuid == null) {
            return HuntDebugInfo.idle();
        }
        HuntState state = ACTIVE_HUNTS.get(uuid);
        if (state == null) {
            return HuntDebugInfo.idle();
        }
        return new HuntDebugInfo(
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
        UUID ownerUuid = PREY_OWNERS.get(victimUuid);
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
        UUID ownerUuid = PREY_OWNERS.get(victimUuid);
        if (ownerUuid == null || !ownerUuid.equals(attackerUuid)) {
            return;
        }
        PREY_LAST_HITS.put(victimUuid, new PreyHitRecord(attackerUuid, System.currentTimeMillis()));
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
            cancelActiveHunt(playerRef, state, store, commandBuffer, HUNT_CANCEL_DEATH_TEXT, "red");
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
            cancelActiveHunt(playerRef, state, store, commandBuffer, HUNT_CANCEL_APPROACH_TIMEOUT_TEXT, "yellow");
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
            cancelActiveHunt(playerRef, state, store, commandBuffer, HUNT_CANCEL_WAYPOINT_TIMEOUT_TEXT, "yellow");
            return;
        }
        float waypointCancelDistance = VampirismConfig.get().getNightHuntWaypointCancelDistance();
        if (waypointCancelDistance > 0f
                && horizontalDistance(playerTransform.getPosition(), state.destination) > waypointCancelDistance) {
            cancelActiveHunt(playerRef, state, store, commandBuffer, HUNT_CANCEL_WAYPOINT_DISTANCE_TEXT, "yellow");
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
        sendPlayerMessage(playerRef, store, HUNT_SUMMON_TEXT, "red");
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
                sendPlayerMessage(playerRef, store, HUNT_FAIL_TEXT, "yellow");
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
        if (state.destination == null) {
            return;
        }

        state.guidePulseAccumulator += dt;
        if (state.guidePulseAccumulator < VampirismConfig.get().getNightHuntGuidePulseIntervalSeconds()) {
            return;
        }
        state.guidePulseAccumulator = 0f;

        UUID ownerUuid = extractPlayerUuid(playerRef, store);
        if (ownerUuid == null) {
            return;
        }
        int pulseCount = Math.min(VampirismConfig.get().getNightHuntMaxActiveWisps(), pulsesPerTier(state.visualTier));
        if (state.guideWisps.size() >= VampirismConfig.get().getNightHuntMaxActiveWisps()) {
            return;
        }
        for (int i = 0; i < pulseCount && state.guideWisps.size() + i < VampirismConfig.get().getNightHuntMaxActiveWisps(); i++) {
            final int pulseIndex = i;
            commandBuffer.run(bufferStore -> spawnGuidePulse(ownerUuid, playerTransform, state, pulseIndex, pulseCount, bufferStore));
        }
    }

    private static void spawnGuidePulse(@Nonnull UUID ownerUuid,
                                        @Nonnull TransformComponent playerTransform,
                                        @Nonnull HuntState state,
                                        int pulseIndex,
                                        int pulseCount,
                                        @Nonnull Store<EntityStore> store) {
        Vector3d destination = state.destination;
        if (destination == null) {
            return;
        }
        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return;
        }

        Vector3d origin = new Vector3d(playerTransform.getPosition());
        Vector3d targetPosition = guideTargetPosition(destination);
        Vector3d direction = guideDirection(origin, targetPosition, state.routeYawDegrees);
        if (direction.length() < 0.001d) {
            return;
        }

        Vector3d sideVector = lateralGuideDirection(direction, state.routeYawDegrees);
        double lateralOffset = pulseCount <= 1 ? 0.0d : (pulseIndex - ((pulseCount - 1) / 2.0d)) * 0.35d;

        Vector3d spawnPosition = new Vector3d(origin)
                .addScaled(direction, VampirismConfig.get().getNightHuntGuideSpawnForwardOffset())
                .addScaled(sideVector, lateralOffset)
                .add(0.0d, VampirismConfig.get().getNightHuntGuideSpawnYOffset(), 0.0d);
        Vector3d adjustedTarget = new Vector3d(targetPosition).addScaled(sideVector, lateralOffset);
        Vector3d launchDirection = guideDirection(spawnPosition, adjustedTarget, state.routeYawDegrees);
        if (launchDirection.length() < 0.001d) {
            return;
        }
        Vector3f rotation = Vector3f.lookAt(launchDirection);

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                time, GUIDE_PROJECTILE_ID, spawnPosition, rotation);
        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return;
        }

        if (projectile.getProjectile() == null && !projectile.initialize()) {
            return;
        }
        if (projectile.getProjectile() == null) {
            return;
        }

        projectile.shoot(holder, ownerUuid,
                spawnPosition.getX(), spawnPosition.getY(), spawnPosition.getZ(),
                rotation.getYaw(), rotation.getPitch());
        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics != null) {
            Vector3d velocity = new Vector3d(launchDirection);
            velocity.setLength(Math.max(0.001d, projectile.getProjectile().getTerminalVelocity()));
            physics.setVelocity(velocity);
        }

        Ref<EntityStore> guideRef = new Ref<>(store);
        store.addEntity(holder, guideRef, AddReason.SPAWN);
        state.guideWisps.add(new GuideWispState(
                guideRef,
                VampirismConfig.get().getNightHuntWispLiftDurationSeconds(),
                new Vector3d(launchDirection).normalize()));
    }

    private static void updateGuideWisps(float dt,
                                         @Nonnull HuntState state,
                                         @Nonnull Store<EntityStore> store,
                                         @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (state.destination == null || state.guideWisps.isEmpty()) {
            return;
        }

        Iterator<GuideWispState> iterator = state.guideWisps.iterator();
        while (iterator.hasNext()) {
            GuideWispState guideWisp = iterator.next();
            Ref<EntityStore> guideRef = guideWisp.ref();
            if (guideRef == null || !guideRef.isValid()) {
                iterator.remove();
                continue;
            }

            TransformComponent transform = (TransformComponent) store.getComponent(guideRef, TransformComponent.getComponentType());
            if (transform == null) {
                iterator.remove();
                continue;
            }

            CollisionResultComponent collisionResult = (CollisionResultComponent) store.getComponent(
                    guideRef, CollisionResultComponent.getComponentType());
            if (collisionResult != null
                    && collisionResult.getCollisionResult() != null
                    && collisionResult.getCollisionResult().getBlockCollisionCount() > 0) {
                commandBuffer.tryRemoveEntity(guideRef, RemoveReason.REMOVE);
                iterator.remove();
                continue;
            }

            Vector3d targetPosition = guideTargetPosition(state.destination);
            if (distance(transform.getPosition(), targetPosition) <= VampirismConfig.get().getNightHuntWispDestinationRadius()) {
                commandBuffer.tryRemoveEntity(guideRef, RemoveReason.REMOVE);
                iterator.remove();
                continue;
            }

            ProjectileComponent projectile = (ProjectileComponent) store.getComponent(guideRef, ProjectileComponent.getComponentType());
            if (projectile == null || projectile.getProjectile() == null) {
                continue;
            }
            SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
            if (physics == null) {
                continue;
            }

            if (guideWisp.liftRemainingSeconds() > 0f) {
                guideWisp.liftRemainingSeconds(Math.max(0f, guideWisp.liftRemainingSeconds() - dt));
                guideWisp.currentDirection(new Vector3d(0.0d, 1.0d, 0.0d));
                Vector3d liftVelocity = new Vector3d(0.0d,
                        Math.max(0.001d, projectile.getProjectile().getTerminalVelocity()),
                        0.0d);
                transform.setRotation(Vector3f.lookAt(liftVelocity));
                physics.setVelocity(liftVelocity);
                continue;
            }

            Vector3d desiredDirection = guideDirection(transform.getPosition(), targetPosition, state.routeYawDegrees);
            if (desiredDirection.length() < 0.001d) {
                commandBuffer.tryRemoveEntity(guideRef, RemoveReason.REMOVE);
                iterator.remove();
                continue;
            }

            Vector3d smoothedDirection = smoothGuideDirection(
                    guideWisp.currentDirection(),
                    desiredDirection,
                    dt);
            guideWisp.currentDirection(smoothedDirection);

            Vector3d velocity = new Vector3d(smoothedDirection);
            velocity.setLength(Math.max(0.001d, projectile.getProjectile().getTerminalVelocity()));
            transform.setRotation(Vector3f.lookAt(smoothedDirection));
            physics.setVelocity(velocity);
        }
    }

    private static void syncWaypointDisplay(float dt,
                                            @Nonnull HuntState state,
                                            boolean markerActive,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (state.destination == null) {
            clearWaypointDisplay(state, commandBuffer);
            return;
        }

        if (state.waypointDisplayRef != null
                && state.waypointDisplayRef.isValid()
                && (state.waypointDisplayActive != markerActive || state.waypointDisplayTier != state.visualTier)) {
            clearWaypointDisplay(state, commandBuffer);
        }

        Ref<EntityStore> displayRef = state.waypointDisplayRef;
        TransformComponent displayTransform = displayRef != null && displayRef.isValid()
                ? (TransformComponent) store.getComponent(displayRef, TransformComponent.getComponentType())
                : null;
        if (displayTransform == null) {
            commandBuffer.run(bufferStore -> spawnWaypointDisplay(state, markerActive, bufferStore));
            return;
        }

        state.waypointDisplayUpdateAccumulator += dt;
        if (state.waypointDisplayUpdateAccumulator < VampirismConfig.get().getNightHuntWaypointUpdateIntervalSeconds()) {
            return;
        }
        state.waypointDisplayUpdateAccumulator = 0f;
        state.waypointRotationDegrees = wrapDegrees(state.waypointRotationDegrees
                + VampirismConfig.get().getNightHuntWaypointRotationSpeedDegrees() * dt);
        displayTransform.teleportPosition(waypointDisplayPosition(state.destination));
        displayTransform.setRotation(new Vector3f(state.waypointRotationDegrees, 0f, 0f));

        ProjectileComponent projectile = (ProjectileComponent) store.getComponent(
                displayRef, ProjectileComponent.getComponentType());
        if (projectile != null) {
            zeroVelocity(projectile);
        }
    }

    private static void spawnWaypointDisplay(@Nonnull HuntState state,
                                             boolean markerActive,
                                             @Nonnull Store<EntityStore> store) {
        if (state.destination == null) {
            return;
        }

        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            return;
        }

        Vector3d position = waypointDisplayPosition(state.destination);
        Vector3f rotation = new Vector3f(state.waypointRotationDegrees, 0f, 0f);
        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                time, waypointProjectileId(state.visualTier, markerActive), position, rotation);
        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            return;
        }

        if (projectile.getProjectile() == null && !projectile.initialize()) {
            return;
        }
        if (projectile.getProjectile() == null) {
            return;
        }

        projectile.shoot(holder, UUID.randomUUID(),
                position.getX(), position.getY(), position.getZ(),
                rotation.getYaw(), rotation.getPitch());
        zeroPhysics(projectile, holder);

        Ref<EntityStore> displayRef = new Ref<>(store);
        store.addEntity(holder, displayRef, AddReason.SPAWN);
        state.waypointDisplayRef = displayRef;
        state.waypointDisplayActive = markerActive;
        state.waypointDisplayTier = state.visualTier;
    }

    private static void spawnMarkedPrey(@Nonnull UUID ownerUuid,
                                        @Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull HuntState state,
                                        @Nonnull Store<EntityStore> store) {
        if (state.destination == null) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
            return;
        }

        clearGuideWisps(state, null);
        removeEntityImmediately(state.waypointDisplayRef);
        state.waypointDisplayRef = null;
        state.waypointDisplayActive = false;

        NightHuntSpawnRegistry.SpawnOption spawnOption = NightHuntSpawnRegistry.get().pickSpawn(
                new NightHuntSpawnRegistry.SpawnContext(
                        PlayerSkillRegistry.get().getAcquiredSkillPoints(ownerUuid),
                        state.completedWaypoints,
                        state.forced,
                        currentHour(store),
                        state.visualTier));

        if (!spawnPrey(ownerUuid, playerRef, state, store, state.destination,
                spawnOption.roleId(), spawnOption.displayName(), spawnOption.dropPoints(),
                spawnOption.archetype(), Math.max(state.visualTier, spawnOption.visualTier()), spawnOption.elite(),
                spawnOption.healthMultiplier(), spawnOption.helperRoleId(), spawnOption.helperCount(),
                spawnOption.helperSpreadRadius(), spawnOption.onSpawnMessage(), spawnOption.preyLifetimeSeconds())) {
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds());
        }
    }

    private static boolean spawnPrey(@Nonnull UUID ownerUuid,
                                     @Nonnull Ref<EntityStore> playerRef,
                                     @Nonnull HuntState state,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull Vector3d spawnPosition,
                                     @Nonnull String roleId,
                                     @Nonnull String displayName,
                                     int rewardPoints,
                                     @Nonnull String archetype,
                                     int visualTier,
                                     boolean elite,
                                     float healthMultiplier,
                                     @Nullable String helperRoleId,
                                     int helperCount,
                                     double helperSpreadRadius,
                                     @Nullable String onSpawnMessage,
                                     float preyLifetimeSeconds) {
        Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> spawn =
                NPCPlugin.get().spawnNPC(store, roleId, null, spawnPosition, new Vector3f());
        if (spawn == null || spawn.first() == null) {
            LOGGER.atWarning().log("[NightMarkedVictimSystem] Failed to spawn marked prey for role " + roleId);
            return false;
        }

        Ref<EntityStore> preyRef = spawn.first();
        UUID preyUuid = extractEntityUuid(preyRef, store);
        if (preyUuid == null) {
            LOGGER.atWarning().log("[NightMarkedVictimSystem] Spawned prey without UUID");
            if (preyRef.isValid()) {
                store.removeEntity(preyRef, RemoveReason.REMOVE);
            }
            return false;
        }

        store.putComponent(preyRef, DisplayNameComponent.getComponentType(),
                new DisplayNameComponent(Message.raw(displayName).color(elite ? "red" : "dark_red")));
        Nameplate nameplate = (Nameplate) store.ensureAndGetComponent(preyRef, Nameplate.getComponentType());
        nameplate.setText(elite ? "[ELITE] " + displayName : displayName);
        applyHealthMultiplier(preyRef, healthMultiplier, store);

        state.phase = HuntPhase.PREY_ACTIVE;
        state.ownerUuid = ownerUuid;
        state.ownerPlayerRef = playerRef;
        state.preyRef = preyRef;
        state.preyEntityUuid = preyUuid;
        state.preyLifetimeRemainingSeconds = preyLifetimeSeconds > 0f
                ? preyLifetimeSeconds
                : VampirismConfig.get().getNightHuntPreyLifetimeSeconds();
        state.summonRemainingSeconds = 0f;
        state.preyRewardPoints = Math.max(0, rewardPoints);
        state.visualTier = clampVisualTier(visualTier);
        clearPreyTracking(preyUuid);
        PREY_OWNERS.put(preyUuid, ownerUuid);
        clearHelperRefs(state, null);
        spawnHelpers(state, preyRef, helperRoleId, helperCount, helperSpreadRadius, store);
        sendPlayerMessage(playerRef, store, onSpawnMessage != null ? onSpawnMessage : HUNT_SPAWN_TEXT, "red");
        return true;
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
        state.approachMarkerId = markerIdFor(extractPlayerUuid(playerRef, store));
        state.approachMarkerWorldName = world.getName();
        clearHelperRefs(state, null);
        state.forced = forced;
        setApproachMarker(state, playerRef, store, world);
        sendPlayerMessage(playerRef, store, HUNT_START_TEXT, "dark_red");
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
        sendPlayerMessage(playerRef, store, HUNT_TRAIL_TEXT, "dark_red");
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
        sendPlayerMessage(playerRef, store, HUNT_SUMMON_TEXT, "red");
    }

    @Nullable
    private static Vector3d findHuntDestination(@Nonnull Vector3d origin,
                                                float baseYaw,
                                                @Nonnull World world,
                                                double minDistance,
                                                double maxDistance) {
        return findHuntDestination(origin, baseYaw, world, minDistance, maxDistance, false);
    }

    @Nullable
    private static Vector3d findHuntDestinationAllowLoading(@Nonnull Vector3d origin,
                                                            float baseYaw,
                                                            @Nonnull World world,
                                                            double minDistance,
                                                            double maxDistance) {
        return findHuntDestination(origin, baseYaw, world, minDistance, maxDistance, true);
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

    private static void clearTransientHunt(@Nonnull HuntState state, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        clearApproachMarker(state);
        clearGuideWisps(state, commandBuffer);
        clearWaypointDisplay(state, commandBuffer);
        clearHelperRefs(state, commandBuffer);
        if (state.preyRef != null && state.preyRef.isValid()) {
            commandBuffer.tryRemoveEntity(state.preyRef, RemoveReason.REMOVE);
        }
        clearPreyTracking(state.preyEntityUuid);
        state.phase = HuntPhase.IDLE;
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
        state.forced = false;
        clearPendingRoute(state);
    }

    private static void resetToIdle(@Nonnull HuntState state, float cooldownSeconds) {
        clearPreyTracking(state.preyEntityUuid);
        clearApproachMarker(state);
        clearHelperRefs(state, null);
        state.phase = HuntPhase.IDLE;
        state.destination = null;
        state.summonRemainingSeconds = 0f;
        state.preyLifetimeRemainingSeconds = 0f;
        state.preyRef = null;
        state.preyEntityUuid = null;
        state.preyRewardPoints = 0;
        state.waypointDisplayRef = null;
        state.cooldownRemainingSeconds = Math.max(state.cooldownRemainingSeconds, cooldownSeconds);
        state.idleDelayRemainingSeconds = randomIdleDelaySeconds();
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
        state.forced = false;
        clearPendingRoute(state);
    }

    private static void clearWaypointDisplay(@Nonnull HuntState state, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> displayRef = state.waypointDisplayRef;
        state.waypointDisplayRef = null;
        state.waypointDisplayActive = false;
        state.waypointDisplayTier = 1;
        state.waypointDisplayUpdateAccumulator = 0f;
        if (displayRef != null && displayRef.isValid()) {
            commandBuffer.tryRemoveEntity(displayRef, RemoveReason.REMOVE);
        }
    }

    private static void clearGuideWisps(@Nonnull HuntState state, @Nullable CommandBuffer<EntityStore> commandBuffer) {
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
                sendPlayerMessage(playerRef, store, HUNT_FAIL_TEXT, "yellow");
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
        World world = resolveWorld(store);
        Vector3d spawnPosition = playerTransform.getPosition();
        if (world != null) {
            Vector3d candidate = findHuntDestination(
                    playerTransform.getPosition(),
                    playerTransform.getRotation() != null ? playerTransform.getRotation().getYaw() + 180.0f : 180.0f,
                    world,
                    4.0d,
                    8.0d);
            if (candidate != null) {
                spawnPosition = candidate;
            }
        }

        if (!spawnPrey(ownerUuid, playerRef, state, store, spawnPosition,
                failState.ambushRoleId(), failState.ambushDisplayName() != null ? failState.ambushDisplayName() : "Blood Ambusher",
                failState.ambushDropPoints(), failState.ambushArchetype(), failState.ambushVisualTier(),
                false, failState.ambushHealthMultiplier(), failState.ambushHelperRoleId(),
                failState.ambushHelperCount(), failState.ambushHelperSpreadRadius(),
                failState.text(), failState.ambushLifetimeSeconds())) {
            resetToIdle(state, failState.cooldownSeconds() > 0f
                    ? failState.cooldownSeconds()
                    : VampirismConfig.get().getNightHuntFailedCooldownSeconds());
        }
    }

    private static void spawnHelpers(@Nonnull HuntState state,
                                     @Nonnull Ref<EntityStore> preyRef,
                                     @Nullable String helperRoleId,
                                     int helperCount,
                                     double helperSpreadRadius,
                                     @Nonnull Store<EntityStore> store) {
        if (helperRoleId == null || helperCount <= 0) {
            return;
        }

        TransformComponent preyTransform = (TransformComponent) store.getComponent(preyRef, TransformComponent.getComponentType());
        if (preyTransform == null) {
            return;
        }

        Vector3d center = preyTransform.getPosition();
        for (int i = 0; i < helperCount; i++) {
            double angle = (Math.PI * 2.0d * i) / Math.max(1, helperCount);
            Vector3d spawnPosition = new Vector3d(
                    center.x + Math.cos(angle) * helperSpreadRadius,
                    center.y,
                    center.z + Math.sin(angle) * helperSpreadRadius);
            Pair<Ref<EntityStore>, com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter> spawn =
                    NPCPlugin.get().spawnNPC(store, helperRoleId, null, spawnPosition, new Vector3f());
            if (spawn == null || spawn.first() == null) {
                continue;
            }
            state.helperRefs.add(spawn.first());
        }
    }

    private static void applyHealthMultiplier(@Nonnull Ref<EntityStore> preyRef,
                                              float healthMultiplier,
                                              @Nonnull Store<EntityStore> store) {
        EntityStatMap stats = (EntityStatMap) store.getComponent(preyRef, EntityStatMap.getComponentType());
        if (stats == null || healthMultiplier <= 1.0f) {
            return;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return;
        }
        float desiredBonus = Math.max(0f, health.getMax() * (healthMultiplier - 1.0f));
        if (desiredBonus <= 0f) {
            return;
        }
        stats.putModifier(DefaultEntityStatTypes.getHealth(), PREY_HEALTH_MODIFIER_KEY,
                new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, desiredBonus));
        stats.addStatValue(DefaultEntityStatTypes.getHealth(), desiredBonus);
        stats.update();
    }

    private static int targetWaypointCount(@Nonnull HuntState state) {
        return Math.max(1, VampirismConfig.get().getNightHuntWaypointCount() + state.bonusWaypoints);
    }

    private static int pulsesPerTier(int visualTier) {
        return switch (clampVisualTier(visualTier)) {
            case 3 -> 3;
            case 2 -> 2;
            default -> 1;
        };
    }

    @Nonnull
    private static String waypointProjectileId(int visualTier, boolean markerActive) {
        return switch (clampVisualTier(visualTier)) {
            case 3 -> markerActive ? WAYPOINT_PROJECTILE_ACTIVE_TIER3_ID : WAYPOINT_PROJECTILE_TIER3_ID;
            case 2 -> markerActive ? WAYPOINT_PROJECTILE_ACTIVE_TIER2_ID : WAYPOINT_PROJECTILE_TIER2_ID;
            default -> markerActive ? WAYPOINT_PROJECTILE_ACTIVE_ID : WAYPOINT_PROJECTILE_ID;
        };
    }

    private static int computeBaseVisualTier(int acquiredPoints) {
        if (acquiredPoints >= 10) {
            return 3;
        }
        if (acquiredPoints >= 4) {
            return 2;
        }
        return 1;
    }

    private static int clampVisualTier(int visualTier) {
        return Math.max(1, Math.min(3, visualTier));
    }

    private static int currentHour(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        return worldTime != null ? worldTime.getCurrentHour() : -1;
    }

    @Nonnull
    private static HuntState createRestoredState(@Nonnull UUID uuid) {
        HuntState state = new HuntState();
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
        if (state.destination == null) {
            return;
        }
        Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlayerWorldData worldData = player.getPlayerConfigData().getPerWorldData(world.getName());
        String markerId = state.approachMarkerId;
        if (markerId == null || markerId.isBlank()) {
            markerId = markerIdFor(extractPlayerUuid(playerRef, store));
            state.approachMarkerId = markerId;
        }
        state.approachMarkerWorldName = world.getName();
        removeAllApproachMarkers(player);
        UserMapMarker marker = new UserMapMarker();
        marker.setId(markerId);
        marker.setPosition((float) state.destination.x, (float) state.destination.z);
        marker.setName(APPROACH_MARKER_NAME);
        marker.setIcon(APPROACH_MARKER_ICON);
        UUID ownerUuid = extractPlayerUuid(playerRef, store);
        if (ownerUuid != null) {
            marker.withCreatedByUuid(ownerUuid);
        }
        PlayerRef ownerRef = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        marker.withCreatedByName(ownerRef != null ? ownerRef.getUsername() : player.getDisplayName());
        worldData.addUserMapMarker(marker);
    }

    private static void clearApproachMarker(@Nonnull HuntState state) {
        String markerWorldName = state.approachMarkerWorldName;
        state.approachMarkerId = null;
        state.approachMarkerWorldName = null;
        Ref<EntityStore> ownerPlayerRef = state.ownerPlayerRef;
        if (ownerPlayerRef == null || !ownerPlayerRef.isValid()) {
            return;
        }
        Store<EntityStore> store = ownerPlayerRef.getStore();
        if (store == null || store.isShutdown()) {
            return;
        }
        if (store.isInThread()) {
            clearApproachMarkerInStore(ownerPlayerRef, store, markerWorldName);
            return;
        }
        World world = resolveWorld(store);
        if (world != null) {
            world.execute(() -> clearApproachMarkerInStore(ownerPlayerRef, store, markerWorldName));
        }
    }

    private static void clearApproachMarkerInStore(@Nonnull Ref<EntityStore> ownerPlayerRef,
                                                   @Nonnull Store<EntityStore> store,
                                                   @Nullable String markerWorldName) {
        if (store.isShutdown() || !ownerPlayerRef.isValid()) {
            return;
        }
        Player player = (Player) store.getComponent(ownerPlayerRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (markerWorldName != null && !markerWorldName.isBlank()) {
            removeApproachMarkers(player.getPlayerConfigData().getPerWorldData(markerWorldName));
        }
        removeAllApproachMarkers(player);
    }

    private static void removeAllApproachMarkers(@Nonnull Player player) {
        for (Map.Entry<String, PlayerWorldData> entry : player.getPlayerConfigData().getPerWorldData().entrySet()) {
            removeApproachMarkers(entry.getValue());
        }
    }

    private static void removeApproachMarkers(@Nonnull PlayerWorldData worldData) {
        List<String> markerIds = new ArrayList<>();
        for (UserMapMarker marker : worldData.getUserMapMarkers()) {
            if (marker == null) {
                continue;
            }
            String markerId = marker.getId();
            if (markerId != null && markerId.startsWith(APPROACH_MARKER_ID_PREFIX)) {
                markerIds.add(markerId);
                continue;
            }
            if (APPROACH_MARKER_ICON.equals(marker.getIcon()) || APPROACH_MARKER_NAME.equals(marker.getName())) {
                if (markerId != null && !markerId.isBlank()) {
                    markerIds.add(markerId);
                }
            }
        }
        if (markerIds.isEmpty()) {
            return;
        }
        for (String markerId : markerIds) {
            worldData.removeUserMapMarker(markerId);
        }
    }

    @Nullable
    private static String markerIdFor(@Nullable UUID uuid) {
        return uuid == null ? null : APPROACH_MARKER_ID_PREFIX + uuid;
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
        if (preyUuid == null) {
            return;
        }
        PREY_OWNERS.remove(preyUuid);
        PREY_LAST_HITS.remove(preyUuid);
    }

    private static boolean tryCompleteTrackedPreyKill(@Nonnull HuntState state,
                                                      @Nonnull Store<EntityStore> store) {
        if (state.preyEntityUuid == null || state.ownerUuid == null) {
            return false;
        }
        PreyHitRecord hitRecord = PREY_LAST_HITS.get(state.preyEntityUuid);
        if (hitRecord == null
                || !state.ownerUuid.equals(hitRecord.attackerUuid())
                || System.currentTimeMillis() - hitRecord.hitAtMs() > PREY_HIT_CREDIT_WINDOW_MS) {
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

        PlayerSkillRegistry.get().incrementCompletedNightHunts(ownerUuid);
        if (rewardPoints > 0) {
            PlayerSkillRegistry.get().addSkillPoints(ownerUuid, rewardPoints);
            if (rewardRef != null && rewardRef.isValid()) {
                sendPlayerMessage(rewardRef, store, rewardText(rewardPoints), "green");
            }
        }
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
        Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        if (player != null) {
            player.sendMessage(Message.raw(text).color(color));
        }
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
        return Math.toDegrees(Math.atan2(to.z - from.z, to.x - from.x));
    }

    @Nonnull
    private static Vector3d guideTargetPosition(@Nonnull Vector3d destination) {
        return waypointDisplayPosition(destination);
    }

    @Nonnull
    private static Vector3d guideDirection(@Nonnull Vector3d origin,
                                           @Nonnull Vector3d target,
                                           double routeYawDegrees) {
        Vector3d direct = new Vector3d(target).subtract(origin);
        if (direct.length() < 0.001d) {
            return direct;
        }
        Vector3d normalizedDirect = new Vector3d(direct).normalize();

        Vector3d routeDirection = new Vector3d(
                Math.cos(Math.toRadians(routeYawDegrees)),
                0.0d,
                Math.sin(Math.toRadians(routeYawDegrees)));
        if (routeDirection.length() >= 0.001d) {
            routeDirection.normalize();
            normalizedDirect.addScaled(routeDirection, 0.35d);
        }
        if (normalizedDirect.length() < 0.001d) {
            return direct.normalize();
        }
        return normalizedDirect.normalize();
    }

    @Nonnull
    private static Vector3d lateralGuideDirection(@Nonnull Vector3d direction, double routeYawDegrees) {
        Vector3d lateral = new Vector3d(-direction.z, 0.0d, direction.x);
        if (lateral.length() >= 0.001d) {
            return lateral.normalize();
        }
        Vector3d routeDirection = new Vector3d(
                Math.cos(Math.toRadians(routeYawDegrees)),
                0.0d,
                Math.sin(Math.toRadians(routeYawDegrees)));
        if (routeDirection.length() < 0.001d) {
            return new Vector3d(0.0d, 0.0d, 1.0d);
        }
        return new Vector3d(-routeDirection.z, 0.0d, routeDirection.x).normalize();
    }

    @Nonnull
    private static Vector3d smoothGuideDirection(@Nonnull Vector3d currentDirection,
                                                 @Nonnull Vector3d desiredDirection,
                                                 float dt) {
        Vector3d normalizedDesired = new Vector3d(desiredDirection);
        if (normalizedDesired.length() < 0.001d) {
            return new Vector3d(currentDirection);
        }
        normalizedDesired.normalize();

        Vector3d normalizedCurrent = new Vector3d(currentDirection);
        if (normalizedCurrent.length() < 0.001d) {
            return normalizedDesired;
        }
        normalizedCurrent.normalize();

        double turnBlend = Math.max(GUIDE_WISP_MIN_TURN_BLEND,
                Math.min(GUIDE_WISP_MAX_TURN_BLEND, dt * GUIDE_WISP_TURN_BLEND_PER_SECOND));
        Vector3d blendedDirection = new Vector3d(normalizedCurrent);
        blendedDirection.addScaled(normalizedDesired, turnBlend);
        if (blendedDirection.length() < 0.001d) {
            return normalizedDesired;
        }
        blendedDirection.normalize();
        return blendedDirection;
    }

    @Nonnull
    private static Vector3d waypointDisplayPosition(@Nonnull Vector3d destination) {
        return new Vector3d(destination.x,
                destination.y + VampirismConfig.get().getNightHuntWaypointMarkerYOffset(),
                destination.z);
    }

    @Nonnull
    private static String rewardText(int points) {
        return "+" + points + " marked prey skill tree point" + (points == 1 ? "" : "s") + ".";
    }

    private static void zeroPhysics(@Nonnull ProjectileComponent projectile, @Nonnull Holder<EntityStore> holder) {
        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics == null) {
            return;
        }
        zeroVelocity(projectile);
        BoundingBox boundingBox = holder.getComponent(BoundingBox.getComponentType());
        if (boundingBox != null) {
            physics.setGravity(0.0, boundingBox);
            physics.setTerminalVelocities(WAYPOINT_TERMINAL_VELOCITY, WAYPOINT_TERMINAL_VELOCITY, boundingBox);
        }
        physics.setImpactSlowdown(0.0);
        physics.setComputePitch(false);
        physics.setComputeYaw(false);
        physics.setProvideCharacterCollisions(false);
    }

    private static void zeroVelocity(@Nonnull ProjectileComponent projectile) {
        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics != null) {
            physics.setVelocity(new Vector3d());
        }
    }

    private static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        return wrapped < 0f ? wrapped + 360.0f : wrapped;
    }

    private static long beginPendingRoute(@Nonnull HuntState state, @Nonnull PendingRouteKind kind) {
        state.phase = HuntPhase.ROUTE_PENDING;
        state.pendingRouteKind = kind;
        state.destination = null;
        return ++state.pendingRouteRequestId;
    }

    private static void clearPendingRoute(@Nonnull HuntState state) {
        state.pendingRouteKind = null;
    }

    private static boolean isPendingRoute(@Nonnull HuntState state,
                                          long requestId,
                                          @Nonnull PendingRouteKind kind) {
        return state.phase == HuntPhase.ROUTE_PENDING
                && state.pendingRouteKind == kind
                && state.pendingRouteRequestId == requestId;
    }

    private enum HuntPhase {
        IDLE,
        ROUTE_PENDING,
        APPROACHING,
        GUIDING,
        SUMMONING,
        PREY_ACTIVE
    }

    private enum PendingRouteKind {
        START,
        APPROACH,
        WAYPOINT
    }

    public record HuntDebugInfo(@Nonnull String phase,
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
        private static HuntDebugInfo idle() {
            return new HuntDebugInfo("idle", false, 0f, 0f, 0, 1, 0, 1, false, false);
        }
    }

    private static final class HuntState {
        private HuntPhase phase = HuntPhase.IDLE;
        private float cooldownRemainingSeconds = 0f;
        private float idleDelayRemainingSeconds = randomIdleDelaySeconds();
        private float guidePulseAccumulator = 0f;
        private float approachElapsedSeconds = 0f;
        private float waypointElapsedSeconds = 0f;
        private float summonRemainingSeconds = 0f;
        private float preyLifetimeRemainingSeconds = 0f;
        private Vector3d destination;
        private int waypointIndex = 0;
        private int completedWaypoints = 0;
        private int bonusWaypoints = 0;
        private double routeYawDegrees = 0f;
        private int visualTier = 1;
        private float waypointRotationDegrees = 0f;
        private float waypointDisplayUpdateAccumulator = 0f;
        private boolean waypointDisplayActive = false;
        private int waypointDisplayTier = 1;
        private Ref<EntityStore> waypointDisplayRef;
        private String approachMarkerId;
        private String approachMarkerWorldName;
        private final List<GuideWispState> guideWisps = new ArrayList<>();
        private final List<Ref<EntityStore>> helperRefs = new ArrayList<>();
        private Ref<EntityStore> preyRef;
        private UUID preyEntityUuid;
        private UUID ownerUuid;
        private Ref<EntityStore> ownerPlayerRef;
        private int preyRewardPoints = 0;
        private boolean forced = false;
        private long pendingRouteRequestId = 0L;
        private PendingRouteKind pendingRouteKind;
    }

    private static final class GuideWispState {
        private final Ref<EntityStore> ref;
        private float liftRemainingSeconds;
        private Vector3d currentDirection;

        private GuideWispState(@Nonnull Ref<EntityStore> ref,
                               float liftRemainingSeconds,
                               @Nonnull Vector3d currentDirection) {
            this.ref = ref;
            this.liftRemainingSeconds = liftRemainingSeconds;
            this.currentDirection = currentDirection;
        }

        @Nonnull
        private Ref<EntityStore> ref() {
            return ref;
        }

        private float liftRemainingSeconds() {
            return liftRemainingSeconds;
        }

        private void liftRemainingSeconds(float liftRemainingSeconds) {
            this.liftRemainingSeconds = liftRemainingSeconds;
        }

        @Nonnull
        private Vector3d currentDirection() {
            return currentDirection;
        }

        private void currentDirection(@Nonnull Vector3d currentDirection) {
            this.currentDirection = currentDirection;
        }
    }

    private record PreyHitRecord(@Nonnull UUID attackerUuid, long hitAtMs) {}
}
