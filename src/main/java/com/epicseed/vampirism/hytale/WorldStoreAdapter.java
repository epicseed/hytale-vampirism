package com.epicseed.vampirism.hytale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class WorldStoreAdapter {
    private WorldStoreAdapter() {
    }

    @Nullable
    public static World resolveWorld(@Nonnull Store<EntityStore> store) {
        EntityStore entityStore = store.getExternalData();
        return entityStore != null ? entityStore.getWorld() : null;
    }
}
