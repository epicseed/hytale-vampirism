package com.epicseed.vampirism.hytale;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ProjectileAdapter {
    private ProjectileAdapter() {
    }

    @Nullable
    public static UUID extractEntityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        return com.epicseed.epiccore.hytale.ProjectileAdapter.extractEntityUuid(ref, store);
    }

    @Nullable
    public static SpawnedProjectile assemble(@Nonnull Store<EntityStore> store,
                                             @Nonnull String projectileId,
                                             @Nonnull Transform look) {
        com.epicseed.epiccore.hytale.ProjectileAdapter.SpawnedProjectile spawned =
                com.epicseed.epiccore.hytale.ProjectileAdapter.assemble(store, projectileId, look);
        return spawned != null ? new SpawnedProjectile(spawned.holder(), spawned.projectile()) : null;
    }

    public static void shoot(@Nonnull SpawnedProjectile spawned,
                             @Nonnull UUID ownerUuid,
                             @Nonnull Transform look) {
        com.epicseed.epiccore.hytale.ProjectileAdapter.shoot(toCore(spawned), ownerUuid, look);
    }

    public static float baseDamage(@Nonnull ProjectileComponent projectile) {
        return com.epicseed.epiccore.hytale.ProjectileAdapter.baseDamage(projectile);
    }

    public static void setDamageMultiplier(@Nonnull ProjectileComponent projectile, float multiplier) {
        com.epicseed.epiccore.hytale.ProjectileAdapter.setDamageMultiplier(projectile, multiplier);
    }

    public static void scaleVelocity(@Nonnull SpawnedProjectile spawned, float speedMultiplier) {
        com.epicseed.epiccore.hytale.ProjectileAdapter.scaleVelocity(toCore(spawned), speedMultiplier);
    }

    public static void addEntity(@Nonnull Store<EntityStore> store, @Nonnull SpawnedProjectile spawned) {
        com.epicseed.epiccore.hytale.ProjectileAdapter.addEntity(store, toCore(spawned));
    }

    @Nonnull
    private static com.epicseed.epiccore.hytale.ProjectileAdapter.SpawnedProjectile toCore(@Nonnull SpawnedProjectile spawned) {
        return new com.epicseed.epiccore.hytale.ProjectileAdapter.SpawnedProjectile(
                spawned.holder(),
                spawned.projectile());
    }

    public record SpawnedProjectile(Holder<EntityStore> holder, ProjectileComponent projectile) {
    }
}
