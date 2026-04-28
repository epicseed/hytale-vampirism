package com.epicseed.vampirism.systems;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CrimsonUmbrellaVisualSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String EFFECT_ID = "Vampirism_CrimsonUmbrella";
    private static final String DISPLAY_PROJECTILE_ID = "Vampirism_CrimsonUmbrella_Display";
    private static final double DISPLAY_Y_OFFSET = 2.35;
    private static final double DISPLAY_TERMINAL_VELOCITY = 0.001;

    private static final Map<UUID, Ref<EntityStore>> ACTIVE_DISPLAYS = new ConcurrentHashMap<>();
    private static volatile int cachedEffectIndex = Integer.MIN_VALUE;

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

            if (store.getComponent(playerRef, Player.getComponentType()) == null) return;

            PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComponent == null) return;

            UUID uuid = playerRefComponent.getUuid();
            int effectIndex = resolveEffectIndex();
            if (effectIndex < 0) {
                removeDisplay(uuid, commandBuffer);
                return;
            }

            EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                    playerRef, EffectControllerComponent.getComponentType());
            TransformComponent playerTransform = (TransformComponent) store.getComponent(
                    playerRef, TransformComponent.getComponentType());
            if (ec == null || playerTransform == null || !ec.hasEffect(effectIndex)) {
                removeDisplay(uuid, commandBuffer);
                return;
            }

            Ref<EntityStore> displayRef = ACTIVE_DISPLAYS.get(uuid);
            TransformComponent displayTransform = displayRef != null && displayRef.isValid()
                    ? (TransformComponent) store.getComponent(displayRef, TransformComponent.getComponentType())
                    : null;
            if (displayTransform == null) {
                displayRef = spawnDisplay(uuid, playerTransform, store, commandBuffer);
                if (displayRef == null) return;
                return;
            }

            syncDisplay(displayRef, displayTransform, playerTransform, store);
        } catch (Exception e) {
            LOGGER.atSevere().log("[CrimsonUmbrellaVisualSystem] Error: " + e.getMessage());
        }
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        ACTIVE_DISPLAYS.remove(uuid);
    }

    private static int resolveEffectIndex() {
        if (cachedEffectIndex == Integer.MIN_VALUE) {
            int idx = EntityEffect.getAssetMap().getIndex(EFFECT_ID);
            if (idx < 0) return -1;
            cachedEffectIndex = idx;
        }
        return cachedEffectIndex;
    }

    private static Ref<EntityStore> spawnDisplay(@Nonnull UUID ownerUuid,
                                                 @Nonnull TransformComponent playerTransform,
                                                 @Nonnull Store<EntityStore> store,
                                                 @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            LOGGER.atWarning().log("[CrimsonUmbrellaVisualSystem] Missing TimeResource");
            return null;
        }

        Vector3d position = displayPosition(playerTransform);
        Vector3f rotation = new Vector3f(playerTransform.getRotation());
        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                time, DISPLAY_PROJECTILE_ID, position, rotation);
        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            LOGGER.atWarning().log("[CrimsonUmbrellaVisualSystem] Display projectile holder missing component");
            return null;
        }

        holder.ensureComponent(Intangible.getComponentType());
        if (projectile.getProjectile() == null && !projectile.initialize()) {
            LOGGER.atWarning().log("[CrimsonUmbrellaVisualSystem] Failed to initialize display projectile");
            return null;
        }
        if (projectile.getProjectile() == null) {
            LOGGER.atWarning().log("[CrimsonUmbrellaVisualSystem] Display projectile asset not found");
            return null;
        }

        projectile.shoot(holder, ownerUuid, position.getX(), position.getY(), position.getZ(),
                rotation.getYaw(), rotation.getPitch());
        zeroPhysics(projectile, holder);

        Ref<EntityStore> displayRef = new Ref<>(store);
        commandBuffer.addEntity(holder, displayRef, AddReason.SPAWN);
        ACTIVE_DISPLAYS.put(ownerUuid, displayRef);
        return displayRef;
    }

    private static void syncDisplay(@Nonnull Ref<EntityStore> displayRef,
                                    @Nonnull TransformComponent displayTransform,
                                    @Nonnull TransformComponent playerTransform,
                                    @Nonnull Store<EntityStore> store) {
        displayTransform.teleportPosition(displayPosition(playerTransform));
        displayTransform.setRotation(new Vector3f(playerTransform.getRotation()));

        ProjectileComponent projectile = (ProjectileComponent) store.getComponent(
                displayRef, ProjectileComponent.getComponentType());
        if (projectile != null) {
            zeroVelocity(projectile);
        }
    }

    private static Vector3d displayPosition(@Nonnull TransformComponent playerTransform) {
        Vector3d position = new Vector3d(playerTransform.getPosition());
        position.setY(position.getY() + DISPLAY_Y_OFFSET);
        return position;
    }

    private static void zeroPhysics(@Nonnull ProjectileComponent projectile, @Nonnull Holder<EntityStore> holder) {
        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics == null) return;
        zeroVelocity(projectile);
        BoundingBox boundingBox = holder.getComponent(BoundingBox.getComponentType());
        if (boundingBox != null) {
            physics.setGravity(0.0, boundingBox);
            physics.setTerminalVelocities(DISPLAY_TERMINAL_VELOCITY, DISPLAY_TERMINAL_VELOCITY, boundingBox);
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

    private static void removeDisplay(@Nonnull UUID uuid, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        Ref<EntityStore> displayRef = ACTIVE_DISPLAYS.remove(uuid);
        if (displayRef == null) return;
        if (displayRef.isValid()) {
            commandBuffer.tryRemoveEntity(displayRef, com.hypixel.hytale.component.RemoveReason.REMOVE);
        }
    }
}
