package com.epicseed.vampirism.hytale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class InventoryAdapter {

    private InventoryAdapter() {
    }

    @Nullable
    public static InventoryComponent getInventorySection(@Nonnull Ref<EntityStore> playerRef,
                                                        @Nonnull Store<EntityStore> store,
                                                        int sectionId) {
        ComponentType<EntityStore, ? extends InventoryComponent> sectionType = getInventorySectionType(sectionId);
        if (sectionType == null) {
            return null;
        }
        return store.getComponent(playerRef, sectionType);
    }

    public static boolean isPlayerSectionContainer(@Nonnull Ref<EntityStore> playerRef,
                                                   @Nonnull Store<EntityStore> store,
                                                   @Nonnull ItemContainer container,
                                                   @Nonnull int[] playerSectionIds) {
        for (int sectionId : playerSectionIds) {
            InventoryComponent section = getInventorySection(playerRef, store, sectionId);
            if (section != null && section.getInventory() == container) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static ComponentType<EntityStore, ? extends InventoryComponent> getInventorySectionType(int sectionId) {
        // SDK-sensitive: these numeric section IDs are server-jar details.
        return (ComponentType<EntityStore, ? extends InventoryComponent>)
                InventoryComponent.getComponentTypeById(sectionId);
    }
}
