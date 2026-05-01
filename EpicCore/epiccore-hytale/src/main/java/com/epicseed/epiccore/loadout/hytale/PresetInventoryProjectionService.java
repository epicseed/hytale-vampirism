package com.epicseed.epiccore.loadout.hytale;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonInt32;

import com.epicseed.epiccore.hytale.InventoryAdapter;
import com.epicseed.epiccore.loadout.PresetProjectionOperations;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PresetInventoryProjectionService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final PresetProjectionConfig config;
    private final Map<UUID, LinkedHashMap<Integer, ItemStack>> stashedItems = new ConcurrentHashMap<>();

    public PresetInventoryProjectionService(@Nonnull PresetProjectionConfig config) {
        this.config = config;
    }

    public int utilityPresetCount(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        InventoryComponent utilitySection = InventoryAdapter.getInventorySection(playerRef, store, config.utilitySectionId());
        if (utilitySection == null) {
            return config.defaultUtilityPresetCount();
        }
        return Math.max(1, Math.min(config.defaultUtilityPresetCount(), utilitySection.getInventory().getCapacity()));
    }

    public int totalPresetCount(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return utilityPresetCount(playerRef, store) + 1;
    }

    public void sync(@Nonnull UUID uuid,
                     @Nonnull Ref<EntityStore> playerRef,
                     @Nonnull Store<EntityStore> store,
                     boolean shouldProject) {
        removeStrayProxies(playerRef, store);

        InventoryComponent utilitySection = InventoryAdapter.getInventorySection(playerRef, store, config.utilitySectionId());
        if (utilitySection == null) {
            clearPlayer(uuid);
            return;
        }

        ItemContainer utility = utilitySection.getInventory();
        int utilityPresetCount = Math.max(1, Math.min(config.defaultUtilityPresetCount(), utility.getCapacity()));
        ItemContainerSlotStorage utilityStorage = new ItemContainerSlotStorage(utility);

        if (!shouldProject) {
            restoreUtilitySlots(uuid, playerRef, store, utilityStorage);
            return;
        }

        LinkedHashMap<Integer, ItemStack> stash = stashedItems.computeIfAbsent(uuid, ignored -> new LinkedHashMap<>());
        PresetProjectionOperations.projectSlots(
                utilityStorage,
                utilityPresetCount,
                stash,
                PresetInventoryProjectionService::isEmpty,
                this::isProxy,
                this::createProxy);
    }

    public void clearPlayer(@Nonnull UUID uuid) {
        stashedItems.remove(uuid);
    }

    public boolean isProxy(@Nullable ItemStack stack) {
        if (stack == null || ItemStack.isEmpty(stack) || !config.proxyItemId().equals(stack.getItemId())) {
            return false;
        }
        BsonDocument metadata = stack.getMetadata();
        return metadata != null && metadata.getBoolean(config.proxyFlagKey(), BsonBoolean.FALSE).getValue();
    }

    public int presetIndex(@Nullable ItemStack stack) {
        if (!isProxy(stack)) {
            return -1;
        }
        BsonDocument metadata = stack.getMetadata();
        if (metadata == null || !metadata.containsKey(config.proxyIndexKey()) || !metadata.get(config.proxyIndexKey()).isInt32()) {
            return -1;
        }
        return metadata.getInt32(config.proxyIndexKey()).getValue();
    }

    private ItemStack createProxy(int presetIndex) {
        return new ItemStack(config.proxyItemId(), 1)
                .withMetadata(config.proxyFlagKey(), BsonBoolean.TRUE)
                .withMetadata(config.proxyIndexKey(), new BsonInt32(presetIndex));
    }

    private void restoreUtilitySlots(@Nonnull UUID uuid,
                                     @Nonnull Ref<EntityStore> playerRef,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull ItemContainerSlotStorage utilityStorage) {
        LinkedHashMap<Integer, ItemStack> stash = stashedItems.remove(uuid);
        if (stash == null) {
            stash = new LinkedHashMap<>();
        }
        LinkedHashMap<Integer, ItemStack> restoreState = stash;
        java.util.List<ItemStack> failedReturns = PresetProjectionOperations.restoreSlots(
                utilityStorage,
                restoreState,
                PresetInventoryProjectionService::isEmpty,
                this::isProxy,
                stack -> returnToVisibleInventory(playerRef, store, stack));
        for (ItemStack leftover : failedReturns) {
            LOGGER.atWarning().log("[PresetInventoryProjectionService] Could not return stashed item for " + uuid + ".");
        }
    }

    private boolean returnToVisibleInventory(@Nonnull Ref<EntityStore> playerRef,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull ItemStack stack) {
        for (int sectionId : config.restoreSectionIds()) {
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

    private void removeStrayProxies(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        for (int sectionId : config.strayProxySectionIds()) {
            InventoryComponent section = InventoryAdapter.getInventorySection(playerRef, store, sectionId);
            if (section == null) {
                continue;
            }
            PresetProjectionOperations.removeMatching(new ItemContainerSlotStorage(section.getInventory()), this::isProxy);
        }
    }

    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || ItemStack.isEmpty(stack);
    }
}
