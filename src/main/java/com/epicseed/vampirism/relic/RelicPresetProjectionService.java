package com.epicseed.vampirism.relic;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.loadout.hytale.PresetInventoryProjectionService;
import com.epicseed.epiccore.loadout.hytale.PresetProjectionConfig;
import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RelicPresetProjectionService {

    public static final int DEFAULT_UTILITY_PRESET_COUNT = RelicBindingService.DEFAULT_UTILITY_PRESET_COUNT;
    public static final int DEFAULT_PRESET_COUNT = RelicBindingService.DEFAULT_PRESET_COUNT;
    public static final int UTILITY_SECTION_ID = InventoryComponent.UTILITY_SECTION_ID;

    private static final PresetProjectionConfig CONFIG = new PresetProjectionConfig(
            RelicInventoryService.RELIC_ITEM_ID,
            "VampirismRelicPresetProxy",
            "VampirismRelicPresetIndex",
            InventoryComponent.UTILITY_SECTION_ID,
            DEFAULT_UTILITY_PRESET_COUNT,
            new int[] {
                    InventoryComponent.HOTBAR_SECTION_ID,
                    InventoryComponent.STORAGE_SECTION_ID,
                    InventoryComponent.ARMOR_SECTION_ID,
                    InventoryComponent.TOOLS_SECTION_ID,
                    InventoryComponent.BACKPACK_SECTION_ID
            },
            new int[] {
                    InventoryComponent.HOTBAR_SECTION_ID,
                    InventoryComponent.STORAGE_SECTION_ID,
                    InventoryComponent.BACKPACK_SECTION_ID
            });
    private static final PresetInventoryProjectionService SERVICE = new PresetInventoryProjectionService(CONFIG);

    private RelicPresetProjectionService() {
    }

    public static int utilityPresetCount(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return SERVICE.utilityPresetCount(playerRef, store);
    }

    public static int totalPresetCount(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        return SERVICE.totalPresetCount(playerRef, store);
    }

    public static void sync(@Nonnull UUID uuid,
                            @Nonnull Ref<EntityStore> playerRef,
                            @Nonnull Store<EntityStore> store,
                            boolean shouldProject) {
        int totalPresetCount = totalPresetCount(playerRef, store);
        int clampedPreset = RelicBindingService.clampPresetIndex(RelicBindingService.activePresetIndex(uuid), totalPresetCount);
        if (clampedPreset != RelicBindingService.activePresetIndex(uuid)) {
            RelicBindingService.setActivePreset(uuid, clampedPreset);
        }
        SERVICE.sync(uuid, playerRef, store, shouldProject);
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        SERVICE.clearPlayer(uuid);
    }

    public static boolean isPresetProxy(@Nullable ItemStack stack) {
        return SERVICE.isProxy(stack);
    }

    public static int presetIndex(@Nullable ItemStack stack) {
        return SERVICE.presetIndex(stack);
    }
}
