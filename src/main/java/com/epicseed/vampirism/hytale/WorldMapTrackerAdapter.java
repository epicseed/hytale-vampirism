package com.epicseed.vampirism.hytale;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class WorldMapTrackerAdapter {

    private WorldMapTrackerAdapter() {
    }

    public static void syncTransform(@Nonnull Holder<EntityStore> holder,
                                     @Nonnull World world,
                                     boolean clear) {
        com.epicseed.epiccore.hytale.WorldMapTrackerAdapter.syncTransform(holder, world, clear);
    }
}
