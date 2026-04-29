package com.epicseed.vampirism.hytale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class TeleportAdapter {
    private TeleportAdapter() {
    }

    @Nullable
    public static World resolveWorld(@Nonnull Store<EntityStore> store) {
        EntityStore entityStore = store.getExternalData();
        return entityStore != null ? entityStore.getWorld() : null;
    }

    public static void teleportPlayer(@Nonnull Ref<EntityStore> playerRef,
                                      @Nonnull Store<EntityStore> store,
                                      @Nonnull World world,
                                      @Nonnull Vector3d target,
                                      @Nonnull TransformComponent transform) {
        Teleport teleport = Teleport.createForPlayer(world, target, transform.getRotation());
        HeadRotation headRotation = (HeadRotation) store.getComponent(playerRef, HeadRotation.getComponentType());
        if (headRotation != null) {
            teleport.setHeadRotation(headRotation.getRotation());
        }
        store.putComponent(playerRef, Teleport.getComponentType(), teleport);
    }
}
