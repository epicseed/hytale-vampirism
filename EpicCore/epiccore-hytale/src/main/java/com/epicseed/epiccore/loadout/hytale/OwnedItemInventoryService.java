package com.epicseed.epiccore.loadout.hytale;

import java.util.function.IntFunction;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.InventoryAdapter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class OwnedItemInventoryService {

    @Nonnull
    public SyncResult syncOwnership(@Nonnull Ref<EntityStore> playerRef,
                                    @Nonnull Store<EntityStore> store,
                                    @Nonnull OwnedItemInventoryConfig config,
                                    @Nonnull Predicate<ItemStack> isOwnedItem,
                                    @Nonnull IntFunction<ItemStack> createOwnedItem,
                                    boolean shouldHaveItem,
                                    boolean allowAdditions) {
        int allowedQuantity = shouldHaveItem ? 1 : 0;
        boolean removedAny = false;
        int validQuantity = 0;
        ItemLocation firstValidLocation = null;

        for (int sectionId : config.validSectionIds()) {
            InventoryComponent section = InventoryAdapter.getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            ItemContainer container = section.getInventory();
            short capacity = container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!isOwnedItem.test(stack)) {
                    continue;
                }
                int quantity = Math.max(0, stack.getQuantity());
                validQuantity += quantity;
                if (firstValidLocation == null) {
                    firstValidLocation = new ItemLocation(sectionId, slot);
                }
                int keep = Math.min(allowedQuantity, quantity);
                int remove = quantity - keep;
                if (remove > 0) {
                    container.removeItemStackFromSlot(slot, remove);
                    removedAny = true;
                }
                allowedQuantity -= keep;
            }
        }

        int strandedQuantity = 0;
        ItemLocation firstStrandedLocation = null;
        if (shouldHaveItem) {
            for (int sectionId : config.strandedSectionIds()) {
                InventoryComponent section = InventoryAdapter.getInventorySection(playerRef, store, sectionId);
                if (section == null) {
                    continue;
                }
                ItemContainer container = section.getInventory();
                short capacity = container.getCapacity();
                for (short slot = 0; slot < capacity; slot++) {
                    ItemStack stack = container.getItemStack(slot);
                    if (!isOwnedItem.test(stack)) {
                        continue;
                    }
                    strandedQuantity += Math.max(0, stack.getQuantity());
                    if (firstStrandedLocation == null) {
                        firstStrandedLocation = new ItemLocation(sectionId, slot);
                    }
                }
            }
        } else {
            removedAny |= removeMatching(playerRef, store, config.strandedSectionIds(), isOwnedItem);
        }

        boolean added = false;
        boolean inventoryFull = false;
        if (allowedQuantity > 0 && allowAdditions) {
            added = addOwnedItem(playerRef, store, config.validSectionIds(), createOwnedItem.apply(allowedQuantity));
            inventoryFull = !added;
        }

        if (shouldHaveItem && (added || validQuantity > 0)) {
            removedAny |= removeMatching(playerRef, store, config.strandedSectionIds(), isOwnedItem);
            strandedQuantity = 0;
            firstStrandedLocation = null;
        }

        boolean hasRequiredItem = shouldHaveItem ? allowedQuantity <= 0 || added : false;
        int foundQuantity = validQuantity + strandedQuantity;
        ItemLocation firstLocation = firstValidLocation != null ? firstValidLocation : firstStrandedLocation;
        return new SyncResult(
                hasRequiredItem,
                inventoryFull,
                added,
                removedAny,
                foundQuantity,
                firstLocation,
                strandedQuantity,
                firstStrandedLocation);
    }

    private static boolean addOwnedItem(@Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull int[] validSectionIds,
                                        @Nonnull ItemStack ownedItem) {
        for (int sectionId : validSectionIds) {
            InventoryComponent section = InventoryAdapter.getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            ItemContainer container = section.getInventory();
            if (!container.canAddItemStack(ownedItem)) {
                continue;
            }
            container.addItemStack(ownedItem);
            return true;
        }
        return false;
    }

    private static boolean removeMatching(@Nonnull Ref<EntityStore> playerRef,
                                          @Nonnull Store<EntityStore> store,
                                          @Nonnull int[] sectionIds,
                                          @Nonnull Predicate<ItemStack> matcher) {
        boolean removedAny = false;
        for (int sectionId : sectionIds) {
            InventoryComponent section = InventoryAdapter.getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            ItemContainer container = section.getInventory();
            short capacity = container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!matcher.test(stack)) {
                    continue;
                }
                container.removeItemStackFromSlot(slot, Math.max(1, stack.getQuantity()));
                removedAny = true;
            }
        }
        return removedAny;
    }

    public record SyncResult(boolean hasRequiredItem,
                             boolean inventoryFull,
                             boolean addedItem,
                             boolean removedExtraItems,
                             int foundQuantity,
                             @Nullable ItemLocation firstLocation,
                             int strandedQuantity,
                             @Nullable ItemLocation firstStrandedLocation) {
    }

    public record ItemLocation(int sectionId, short slot) {
    }
}
