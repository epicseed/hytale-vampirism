package com.epicseed.vampirism.hytale;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.InventorySectionSelectionAdapter;
import com.epicseed.epiccore.hytale.InventorySectionSelectionConfig;
import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.epicseed.vampirism.relic.RelicPresetProjectionService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RelicPresetSelectionAdapter {

    private static final InventorySectionSelectionAdapter ADAPTER = new InventorySectionSelectionAdapter(
            new InventorySectionSelectionConfig(RelicPresetProjectionService.UTILITY_SECTION_ID),
            RelicPresetSelectionAdapter::applyPresetSelection);

    private RelicPresetSelectionAdapter() {
    }

    public static synchronized void init() {
        ADAPTER.init();
    }

    public static synchronized void shutdown() {
        ADAPTER.shutdown();
    }

    private static void applyPresetSelection(@Nonnull UUID uuid,
                                             @Nullable Ref<EntityStore> playerEntityRef,
                                             @Nullable Store<EntityStore> store,
                                             int selectedSlot) {
        int utilityPresetCount = playerEntityRef != null && store != null
                ? RelicPresetProjectionService.utilityPresetCount(playerEntityRef, store)
                : RelicPresetProjectionService.DEFAULT_UTILITY_PRESET_COUNT;
        int presetIndex = RelicBindingService.presetIndexForUtilitySelection(
                selectedSlot,
                InventoryComponent.INACTIVE_SLOT_INDEX,
                utilityPresetCount);
        if (presetIndex < 0 || RelicBindingService.activePresetIndex(uuid) == presetIndex) {
            return;
        }
        RelicBindingService.setActivePreset(uuid, presetIndex);
    }
}
