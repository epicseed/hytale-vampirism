package com.epicseed.vampirism.hytale;

import java.lang.reflect.Field;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ProjectileAdapter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Field PROJECTILE_DAMAGE_MODIFIER_FIELD = resolveProjectileDamageModifierField();

    private ProjectileAdapter() {
    }

    @Nullable
    public static UUID extractEntityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComponent = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }

    @Nullable
    public static SpawnedProjectile assemble(@Nonnull Store<EntityStore> store,
                                             @Nonnull String projectileId,
                                             @Nonnull Transform look) {
        TimeResource time = store.getResource(TimeResource.getResourceType());
        if (time == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: missing TimeResource");
            return null;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                time, projectileId, look.getPosition(), look.getRotation());
        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: projectile holder missing component");
            return null;
        }

        holder.ensureComponent(Intangible.getComponentType());
        if (projectile.getProjectile() == null && !projectile.initialize()) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: failed to initialize projectile " + projectileId);
            return null;
        }
        if (projectile.getProjectile() == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: projectile asset not found " + projectileId);
            return null;
        }

        return new SpawnedProjectile(holder, projectile);
    }

    public static void shoot(@Nonnull SpawnedProjectile spawned,
                             @Nonnull UUID ownerUuid,
                             @Nonnull Transform look) {
        Vector3d position = look.getPosition();
        spawned.projectile().shoot(
                spawned.holder(),
                ownerUuid,
                position.getX(),
                position.getY(),
                position.getZ(),
                look.getRotation().getYaw(),
                look.getRotation().getPitch());
    }

    public static float baseDamage(@Nonnull ProjectileComponent projectile) {
        return projectile.getProjectile() != null ? Math.max(0f, projectile.getProjectile().getDamage()) : 0f;
    }

    public static void setDamageMultiplier(@Nonnull ProjectileComponent projectile, float multiplier) {
        if (PROJECTILE_DAMAGE_MODIFIER_FIELD == null) return;
        try {
            PROJECTILE_DAMAGE_MODIFIER_FIELD.setFloat(projectile, Math.max(0f, multiplier));
        } catch (IllegalAccessException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] Failed to tune projectile damage: " + e.getMessage());
        }
    }

    public static void scaleVelocity(@Nonnull SpawnedProjectile spawned, float speedMultiplier) {
        if (Math.abs(speedMultiplier - 1f) < 0.0001f) return;

        ProjectileComponent projectile = spawned.projectile();
        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics == null) return;

        Vector3d velocity = new Vector3d(physics.getVelocity());
        if (velocity.length() > 0d) {
            velocity.setLength(velocity.length() * speedMultiplier);
            physics.setVelocity(velocity);
        }

        BoundingBox boundingBox = spawned.holder().getComponent(BoundingBox.getComponentType());
        if (boundingBox != null && projectile.getProjectile() != null) {
            double terminalVelocity = projectile.getProjectile().getTerminalVelocity() * speedMultiplier;
            physics.setTerminalVelocities(terminalVelocity, terminalVelocity, boundingBox);
        }
    }

    public static void addEntity(@Nonnull Store<EntityStore> store, @Nonnull SpawnedProjectile spawned) {
        store.addEntity(spawned.holder(), AddReason.SPAWN);
    }

    private static Field resolveProjectileDamageModifierField() {
        try {
            Field field = ProjectileComponent.class.getDeclaredField("brokenDamageModifier");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] Projectile damage tuning unavailable: " + e.getMessage());
            return null;
        }
    }

    public record SpawnedProjectile(Holder<EntityStore> holder, ProjectileComponent projectile) {
    }
}
