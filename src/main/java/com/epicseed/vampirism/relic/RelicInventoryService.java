package com.epicseed.vampirism.relic;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RelicInventoryService {

    public static final String RELIC_ITEM_ID = "VampirismRelic";
    private static final Map<Ref<EntityStore>, Long> AUTO_GRANT_SUPPRESSED_UNTIL = new ConcurrentHashMap<>();


    // - -1 hotbar
    // - -2 storage
    // - -3 armor
    // - -5 utility
    // - -8 tools
    // - -9 backpack
    private static final int[] RELIC_VALID_SECTION_IDS = { -1, -2, -5 };
    private static final int[] RELIC_STRANDED_SECTION_IDS = { -3, -8, -9 };

    private RelicInventoryService() {}

    @Nonnull
    public static SyncResult syncOwnership(@Nonnull Ref<EntityStore> playerRef,
                                           @Nonnull Store<EntityStore> store,
                                           boolean shouldHaveRelic) {
        return syncOwnership(playerRef, store, shouldHaveRelic, true);
    }

    @Nonnull
    public static SyncResult syncOwnership(@Nonnull Ref<EntityStore> playerRef,
                                           @Nonnull Store<EntityStore> store,
                                           boolean shouldHaveRelic,
                                           boolean allowAdditions) {
        int allowedQuantity = shouldHaveRelic ? 1 : 0;
        boolean removedAny = false;
        int validQuantity = 0;
        RelicLocation firstValidLocation = null;

        for (int sectionId : RELIC_VALID_SECTION_IDS) {
            InventoryComponent section = getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            ItemContainer container = section.getInventory();
            short capacity = container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (ItemStack.isEmpty(stack) || !RELIC_ITEM_ID.equals(stack.getItemId())) {
                    continue;
                }
                int quantity = Math.max(0, stack.getQuantity());
                validQuantity += quantity;
                if (firstValidLocation == null) {
                    firstValidLocation = new RelicLocation(sectionId, slot);
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
        RelicLocation firstStrandedLocation = null;
        if (shouldHaveRelic) {
            for (int sectionId : RELIC_STRANDED_SECTION_IDS) {
                InventoryComponent section = getInventorySection(playerRef, store, sectionId);
                if (section == null) {
                    continue;
                }
                ItemContainer container = section.getInventory();
                short capacity = container.getCapacity();
                for (short slot = 0; slot < capacity; slot++) {
                    ItemStack stack = container.getItemStack(slot);
                    if (ItemStack.isEmpty(stack) || !RELIC_ITEM_ID.equals(stack.getItemId())) {
                        continue;
                    }
                    strandedQuantity += Math.max(0, stack.getQuantity());
                    if (firstStrandedLocation == null) {
                        firstStrandedLocation = new RelicLocation(sectionId, slot);
                    }
                }
            }
        } else {
            for (int sectionId : RELIC_STRANDED_SECTION_IDS) {
                InventoryComponent section = getInventorySection(playerRef, store, sectionId);
                if (section == null) {
                    continue;
                }
                ItemContainer container = section.getInventory();
                short capacity = container.getCapacity();
                for (short slot = 0; slot < capacity; slot++) {
                    ItemStack stack = container.getItemStack(slot);
                    if (ItemStack.isEmpty(stack) || !RELIC_ITEM_ID.equals(stack.getItemId())) {
                        continue;
                    }
                    container.removeItemStackFromSlot(slot, Math.max(1, stack.getQuantity()));
                    removedAny = true;
                }
            }
        }

        boolean added = false;
        boolean inventoryFull = false;
        if (allowedQuantity > 0 && allowAdditions) {
            added = addRelicToInventory(playerRef, store, allowedQuantity);
            inventoryFull = !added;
        }

        if (shouldHaveRelic && (added || validQuantity > 0)) {
            removedAny |= removeStrandedRelics(playerRef, store);
            strandedQuantity = 0;
            firstStrandedLocation = null;
        }

        boolean hasRequiredRelic = shouldHaveRelic ? allowedQuantity <= 0 || added : false;
        int foundQuantity = validQuantity + strandedQuantity;
        RelicLocation firstLocation = firstValidLocation != null ? firstValidLocation : firstStrandedLocation;
        return new SyncResult(
                hasRequiredRelic,
                inventoryFull,
                added,
                removedAny,
                foundQuantity,
                firstLocation,
                strandedQuantity,
                firstStrandedLocation);
    }

    @Nonnull
    public static SyncResult ensurePresent(@Nonnull Ref<EntityStore> playerRef,
                                           @Nonnull Store<EntityStore> store) {
        return syncOwnership(playerRef, store, true, true);
    }

    public static void suppressAutoGrant(@Nonnull Ref<EntityStore> playerRef, long durationMs) {
        if (durationMs <= 0L) {
            return;
        }
        AUTO_GRANT_SUPPRESSED_UNTIL.put(playerRef, System.currentTimeMillis() + durationMs);
    }

    public static boolean isAutoGrantSuppressed(@Nonnull Ref<EntityStore> playerRef) {
        Long suppressedUntil = AUTO_GRANT_SUPPRESSED_UNTIL.get(playerRef);
        if (suppressedUntil == null) {
            return false;
        }
        if (suppressedUntil <= System.currentTimeMillis()) {
            AUTO_GRANT_SUPPRESSED_UNTIL.remove(playerRef, suppressedUntil);
            return false;
        }
        return true;
    }

    private static boolean addRelicToInventory(@Nonnull Ref<EntityStore> playerRef,
                                               @Nonnull Store<EntityStore> store,
                                               int quantity) {
        ItemStack relicStack = new ItemStack(RELIC_ITEM_ID, quantity);
        for (int sectionId : RELIC_VALID_SECTION_IDS) {
            InventoryComponent section = getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            ItemContainer container = section.getInventory();
            if (!container.canAddItemStack(relicStack)) {
                continue;
            }
            container.addItemStack(relicStack);
            return true;
        }
        return false;
    }

    private static boolean removeStrandedRelics(@Nonnull Ref<EntityStore> playerRef,
                                                @Nonnull Store<EntityStore> store) {
        boolean removedAny = false;
        for (int sectionId : RELIC_STRANDED_SECTION_IDS) {
            InventoryComponent section = getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            ItemContainer container = section.getInventory();
            short capacity = container.getCapacity();
            for (short slot = 0; slot < capacity; slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (ItemStack.isEmpty(stack) || !RELIC_ITEM_ID.equals(stack.getItemId())) {
                    continue;
                }
                container.removeItemStackFromSlot(slot, Math.max(1, stack.getQuantity()));
                removedAny = true;
            }
        }
        return removedAny;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static InventoryComponent getInventorySection(@Nonnull Ref<EntityStore> playerRef,
                                                          @Nonnull Store<EntityStore> store,
                                                          int sectionId) {
        ComponentType<EntityStore, ? extends InventoryComponent> sectionType =
                (ComponentType<EntityStore, ? extends InventoryComponent>)
                        InventoryComponent.getComponentTypeById(sectionId);
        if (sectionType == null) {
            return null;
        }
        return store.getComponent(playerRef, sectionType);
    }

    public record SyncResult(boolean hasRequiredRelic,
                             boolean inventoryFull,
                             boolean addedRelic,
                             boolean removedExtraRelics,
                             int foundQuantity,
                             @Nullable RelicLocation firstLocation,
                             int strandedQuantity,
                             @Nullable RelicLocation firstStrandedLocation) {
    }

    public record RelicLocation(int sectionId, short slot) {
        @Nonnull
        public String describe() {
            return sectionName(sectionId) + " slot " + slot;
        }
    }

    @Nonnull
    private static String sectionName(int sectionId) {
        return switch (sectionId) {
            case -1 -> "hotbar";
            case -2 -> "storage";
            case -3 -> "armor";
            case -5 -> "utility";
            case -8 -> "tools";
            case -9 -> "backpack";
            default -> "section " + sectionId;
        };
    }
}
