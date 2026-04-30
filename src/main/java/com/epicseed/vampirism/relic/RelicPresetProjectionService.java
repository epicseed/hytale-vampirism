package com.epicseed.vampirism.relic;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.epicseed.vampirism.hytale.InventoryAdapter;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RelicPresetProjectionService {

    public static final int DEFAULT_UTILITY_PRESET_COUNT = RelicBindingService.DEFAULT_UTILITY_PRESET_COUNT;
    public static final int DEFAULT_PRESET_COUNT = RelicBindingService.DEFAULT_PRESET_COUNT;
    public static final int UTILITY_SECTION_ID = InventoryComponent.UTILITY_SECTION_ID;

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String PRESET_PROXY_KEY = "VampirismRelicPresetProxy";
    private static final String PRESET_INDEX_KEY = "VampirismRelicPresetIndex";
    private static final int[] STRAY_PROXY_SECTION_IDS = {
            InventoryComponent.HOTBAR_SECTION_ID,
            InventoryComponent.STORAGE_SECTION_ID,
            InventoryComponent.ARMOR_SECTION_ID,
            InventoryComponent.TOOLS_SECTION_ID,
            InventoryComponent.BACKPACK_SECTION_ID
    };
    private static final int[] RESTORE_SECTION_IDS = {
            InventoryComponent.HOTBAR_SECTION_ID,
            InventoryComponent.STORAGE_SECTION_ID,
            InventoryComponent.BACKPACK_SECTION_ID
    };

    private static final Map<UUID, LinkedHashMap<Short, ItemStack>> STASHED_UTILITY_ITEMS = new ConcurrentHashMap<>();

    private RelicPresetProjectionService() {
    }

    public static int utilityPresetCount(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        InventoryComponent utilitySection = InventoryAdapter.getInventorySection(playerRef, store, UTILITY_SECTION_ID);
        if (utilitySection == null) {
            return DEFAULT_UTILITY_PRESET_COUNT;
        }
        return Math.max(1, Math.min(DEFAULT_UTILITY_PRESET_COUNT, utilitySection.getInventory().getCapacity()));
    }

    public static int totalPresetCount(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return utilityPresetCount(playerRef, store) + 1;
    }

    public static void sync(@Nonnull UUID uuid,
                            @Nonnull Ref<EntityStore> playerRef,
                            @Nonnull Store<EntityStore> store,
                            boolean shouldProject) {
        removeStrayPresetProxies(playerRef, store);

        InventoryComponent utilitySection = InventoryAdapter.getInventorySection(playerRef, store, UTILITY_SECTION_ID);
        if (utilitySection == null) {
            clearPlayer(uuid);
            return;
        }

        ItemContainer utility = utilitySection.getInventory();
        int utilityPresetCount = Math.max(1, Math.min(DEFAULT_UTILITY_PRESET_COUNT, utility.getCapacity()));
        int totalPresetCount = utilityPresetCount + 1;
        int clampedPreset = RelicBindingService.clampPresetIndex(RelicBindingService.activePresetIndex(uuid), totalPresetCount);
        if (clampedPreset != RelicBindingService.activePresetIndex(uuid)) {
            RelicBindingService.setActivePreset(uuid, clampedPreset);
        }

        if (!shouldProject) {
            restoreUtilitySlots(uuid, playerRef, store, utility);
            return;
        }

        LinkedHashMap<Short, ItemStack> stash = STASHED_UTILITY_ITEMS.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>());
        for (short slot = 0; slot < utility.getCapacity() && slot < utilityPresetCount; slot++) {
            ItemStack current = utility.getItemStack(slot);
            if (!ItemStack.isEmpty(current) && !isPresetProxy(current)) {
                stash.putIfAbsent(slot, current);
            }
            if (matchesPresetProxy(current, slot)) {
                continue;
            }
            utility.setItemStackForSlot(slot, createPresetProxy(slot));
        }
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        STASHED_UTILITY_ITEMS.remove(uuid);
    }

    public static boolean isPresetProxy(@Nullable ItemStack stack) {
        if (stack == null || ItemStack.isEmpty(stack) || !RelicInventoryService.RELIC_ITEM_ID.equals(stack.getItemId())) {
            return false;
        }
        BsonDocument metadata = stack.getMetadata();
        return metadata != null && metadata.getBoolean(PRESET_PROXY_KEY, BsonBoolean.FALSE).getValue();
    }

    public static int presetIndex(@Nullable ItemStack stack) {
        if (!isPresetProxy(stack)) {
            return -1;
        }
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null || !metadata.containsKey(PRESET_INDEX_KEY) || !metadata.get(PRESET_INDEX_KEY).isInt32()) {
            return -1;
        }
        return metadata.getInt32(PRESET_INDEX_KEY).getValue();
    }

    private static boolean matchesPresetProxy(@Nullable ItemStack stack, int presetIndex) {
        return isPresetProxy(stack) && presetIndex(stack) == presetIndex && Math.max(1, stack.getQuantity()) == 1;
    }

    @Nonnull
    private static ItemStack createPresetProxy(int presetIndex) {
        return new ItemStack(RelicInventoryService.RELIC_ITEM_ID, 1)
                .withMetadata(PRESET_PROXY_KEY, BsonBoolean.TRUE)
                .withMetadata(PRESET_INDEX_KEY, new BsonInt32(presetIndex));
    }

    private static void restoreUtilitySlots(@Nonnull UUID uuid,
                                            @Nonnull Ref<EntityStore> playerRef,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull ItemContainer utility) {
        LinkedHashMap<Short, ItemStack> stash = STASHED_UTILITY_ITEMS.remove(uuid);
        for (short slot = 0; slot < utility.getCapacity(); slot++) {
            ItemStack current = utility.getItemStack(slot);
            ItemStack restore = stash != null ? stash.remove(slot) : null;
            if (restore != null) {
                if (ItemStack.isEmpty(current) || isPresetProxy(current)) {
                    utility.setItemStackForSlot(slot, restore);
                } else if (!returnToVisibleInventory(playerRef, store, restore)) {
                    LOGGER.atWarning().log("[RelicPresetProjectionService] Could not restore stashed utility item for "
                            + uuid + " from slot " + slot + ".");
                }
                continue;
            }
            if (isPresetProxy(current)) {
                utility.removeItemStackFromSlot(slot);
            }
        }

        if (stash == null || stash.isEmpty()) {
            return;
        }
        for (ItemStack leftover : stash.values()) {
            if (!returnToVisibleInventory(playerRef, store, leftover)) {
                LOGGER.atWarning().log("[RelicPresetProjectionService] Could not return leftover stashed utility item for " + uuid + ".");
            }
        }
    }

    private static boolean returnToVisibleInventory(@Nonnull Ref<EntityStore> playerRef,
                                                    @Nonnull Store<EntityStore> store,
                                                    @Nonnull ItemStack stack) {
        for (int sectionId : RESTORE_SECTION_IDS) {
            InventoryComponent section = InventoryAdapter.getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            ItemContainer container = section.getInventory();
            if (!container.canAddItemStack(stack)) {
                continue;
            }
            container.addItemStack(stack);
            return true;
        }
        return false;
    }

    private static void removeStrayPresetProxies(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        for (int sectionId : STRAY_PROXY_SECTION_IDS) {
            InventoryComponent section = InventoryAdapter.getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            ItemContainer container = section.getInventory();
            for (short slot = 0; slot < container.getCapacity(); slot++) {
                ItemStack stack = container.getItemStack(slot);
                if (!isPresetProxy(stack)) {
                    continue;
                }
                container.removeItemStackFromSlot(slot);
            }
        }
    }
}
