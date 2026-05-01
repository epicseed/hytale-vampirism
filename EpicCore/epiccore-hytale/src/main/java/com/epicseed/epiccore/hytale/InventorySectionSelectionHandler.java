package com.epicseed.epiccore.hytale;

import java.util.UUID;

import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

@FunctionalInterface
public interface InventorySectionSelectionHandler {
    void onSelected(UUID uuid,
                    @Nullable Ref<EntityStore> playerEntityRef,
                    @Nullable Store<EntityStore> store,
                    int selectedSlot);
}
