package com.epicseed.vampirism.domain.hunt;

import java.util.Iterator;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.config.VampirismConfig;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.CollisionResultComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntVisualService {
    private static final String GUIDE_PROJECTILE_ID = "Vampirism_NightHunt_Wisp";
    private static final String WAYPOINT_PROJECTILE_ID = "Vampirism_NightHunt_Waypoint";
    private static final String WAYPOINT_PROJECTILE_ACTIVE_ID = "Vampirism_NightHunt_Waypoint_Active";
    private static final String WAYPOINT_PROJECTILE_TIER2_ID = "Vampirism_NightHunt_Waypoint_T2";
    private static final String WAYPOINT_PROJECTILE_ACTIVE_TIER2_ID = "Vampirism_NightHunt_Waypoint_Active_T2";
    private static final String WAYPOINT_PROJECTILE_TIER3_ID = "Vampirism_NightHunt_Waypoint_T3";
    private static final String WAYPOINT_PROJECTILE_ACTIVE_TIER3_ID = "Vampirism_NightHunt_Waypoint_Active_T3";
    private static final double WAYPOINT_TERMINAL_VELOCITY = 0.001d;
    private static final double GUIDE_WISP_MIN_TURN_BLEND = 0.10d;
    private static final double GUIDE_WISP_MAX_TURN_BLEND = 0.24d;
    private static final double GUIDE_WISP_TURN_BLEND_PER_SECOND = 5.5d;

    private NightHuntVisualService() {
    }

    public static void emitGuidePulse(float dt,
                                      @Nullable UUID ownerUuid,
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

    public static void spawnGuidePulse(@Nonnull UUID ownerUuid,
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

    public static void updateGuideWisps(float dt,
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

    public static void syncWaypointDisplay(float dt,
                                           @Nonnull HuntState state,
                                           boolean markerActive,
                                           @Nonnull Store<EntityStore> store,
                                           @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        if (state.destination == null) {
            NightHuntCleanupService.clearWaypointDisplay(state, commandBuffer);
            return;
        }

        if (state.waypointDisplayRef != null
                && state.waypointDisplayRef.isValid()
                && (state.waypointDisplayActive != markerActive || state.waypointDisplayTier != state.visualTier)) {
            NightHuntCleanupService.clearWaypointDisplay(state, commandBuffer);
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

    public static void spawnWaypointDisplay(@Nonnull HuntState state,
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

    public static int pulsesPerTier(int visualTier) {
        return switch (clampVisualTier(visualTier)) {
            case 3 -> 3;
            case 2 -> 2;
            default -> 1;
        };
    }

    @Nonnull
    public static String waypointProjectileId(int visualTier, boolean markerActive) {
        return switch (clampVisualTier(visualTier)) {
            case 3 -> markerActive ? WAYPOINT_PROJECTILE_ACTIVE_TIER3_ID : WAYPOINT_PROJECTILE_TIER3_ID;
            case 2 -> markerActive ? WAYPOINT_PROJECTILE_ACTIVE_TIER2_ID : WAYPOINT_PROJECTILE_TIER2_ID;
            default -> markerActive ? WAYPOINT_PROJECTILE_ACTIVE_ID : WAYPOINT_PROJECTILE_ID;
        };
    }

    public static int computeBaseVisualTier(int acquiredPoints) {
        if (acquiredPoints >= 10) {
            return 3;
        }
        if (acquiredPoints >= 4) {
            return 2;
        }
        return 1;
    }

    public static int clampVisualTier(int visualTier) {
        return Math.max(1, Math.min(3, visualTier));
    }

    @Nonnull
    public static Vector3d guideTargetPosition(@Nonnull Vector3d destination) {
        return waypointDisplayPosition(destination);
    }

    @Nonnull
    public static Vector3d guideDirection(@Nonnull Vector3d origin,
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
    public static Vector3d lateralGuideDirection(@Nonnull Vector3d direction, double routeYawDegrees) {
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
    public static Vector3d smoothGuideDirection(@Nonnull Vector3d currentDirection,
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
    public static Vector3d waypointDisplayPosition(@Nonnull Vector3d destination) {
        return new Vector3d(destination.x,
                destination.y + VampirismConfig.get().getNightHuntWaypointMarkerYOffset(),
                destination.z);
    }

    public static void zeroPhysics(@Nonnull ProjectileComponent projectile, @Nonnull Holder<EntityStore> holder) {
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

    public static void zeroVelocity(@Nonnull ProjectileComponent projectile) {
        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics != null) {
            physics.setVelocity(new Vector3d());
        }
    }

    public static float wrapDegrees(float value) {
        float wrapped = value % 360.0f;
        return wrapped < 0f ? wrapped + 360.0f : wrapped;
    }

    private static double distance(@Nonnull Vector3d a, @Nonnull Vector3d b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
