package com.epicseed.vampirism.hytale;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.epicseed.vampirism.relic.RelicPresetProjectionService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RelicPresetSelectionAdapter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static PacketFilter inboundFilter;

    private RelicPresetSelectionAdapter() {
    }

    public static synchronized void init() {
        if (inboundFilter != null) {
            return;
        }
        inboundFilter = PacketAdapters.registerInbound(RelicPresetSelectionAdapter::handleInbound);
        LOGGER.atInfo().log("[RelicPresetSelectionAdapter] Listening for utility slot selection packets.");
    }

    public static synchronized void shutdown() {
        if (inboundFilter == null) {
            return;
        }
        PacketAdapters.deregisterInbound(inboundFilter);
        inboundFilter = null;
    }

    private static void handleInbound(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (!(packet instanceof SetActiveSlot setActiveSlot)) {
            return;
        }
        if (setActiveSlot.inventorySectionId != RelicPresetProjectionService.UTILITY_SECTION_ID) {
            return;
        }
        schedulePresetSelection(playerRef, setActiveSlot.activeSlot);
    }

    private static void schedulePresetSelection(@Nonnull PlayerRef playerRef, int selectedSlot) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        Store<EntityStore> store = playerEntityRef.getStore();
        if (store == null) {
            applyPresetSelection(playerRef.getUuid(), selectedSlot, RelicPresetProjectionService.DEFAULT_UTILITY_PRESET_COUNT);
            return;
        }
        World world = WorldStoreAdapter.resolveWorld(store);
        if (world == null) {
            applyPresetSelection(playerRef.getUuid(), selectedSlot, RelicPresetProjectionService.DEFAULT_UTILITY_PRESET_COUNT);
            return;
        }
        Runnable action = () -> applyPresetSelection(
                playerRef.getUuid(),
                selectedSlot,
                RelicPresetProjectionService.utilityPresetCount(playerEntityRef, store));
        if (world.isInThread()) {
            action.run();
            return;
        }
        world.execute(action);
    }

    private static void applyPresetSelection(@Nonnull java.util.UUID uuid, int selectedSlot, int utilityPresetCount) {
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
