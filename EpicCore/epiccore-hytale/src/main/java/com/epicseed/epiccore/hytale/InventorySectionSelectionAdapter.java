package com.epicseed.epiccore.hytale;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.inventory.SetActiveSlot;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class InventorySectionSelectionAdapter {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final InventorySectionSelectionConfig config;
    private final InventorySectionSelectionHandler selectionHandler;
    private PacketFilter inboundFilter;

    public InventorySectionSelectionAdapter(@Nonnull InventorySectionSelectionConfig config,
                                           @Nonnull InventorySectionSelectionHandler selectionHandler) {
        this.config = config;
        this.selectionHandler = selectionHandler;
    }

    public synchronized void init() {
        if (inboundFilter != null) {
            return;
        }
        inboundFilter = PacketAdapters.registerInbound(this::handleInbound);
        LOGGER.atInfo().log("[InventorySectionSelectionAdapter] Listening for inventory slot selection packets.");
    }

    public synchronized void shutdown() {
        if (inboundFilter == null) {
            return;
        }
        PacketAdapters.deregisterInbound(inboundFilter);
        inboundFilter = null;
    }

    private void handleInbound(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        if (!(packet instanceof SetActiveSlot setActiveSlot)) {
            return;
        }
        if (setActiveSlot.inventorySectionId != config.inventorySectionId()) {
            return;
        }
        scheduleSelection(playerRef, setActiveSlot.activeSlot);
    }

    private void scheduleSelection(@Nonnull PlayerRef playerRef, int selectedSlot) {
        Ref<EntityStore> playerEntityRef = playerRef.getReference();
        Store<EntityStore> store = playerEntityRef.getStore();
        if (store == null) {
            selectionHandler.onSelected(playerRef.getUuid(), null, null, selectedSlot);
            return;
        }
        World world = WorldStoreAdapter.resolveWorld(store);
        if (world == null) {
            selectionHandler.onSelected(playerRef.getUuid(), null, null, selectedSlot);
            return;
        }
        Runnable action = () -> selectionHandler.onSelected(playerRef.getUuid(), playerEntityRef, store, selectedSlot);
        if (world.isInThread()) {
            action.run();
            return;
        }
        world.execute(action);
    }
}
