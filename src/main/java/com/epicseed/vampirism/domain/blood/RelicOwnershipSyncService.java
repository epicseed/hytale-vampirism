package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.relic.RelicInventoryService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RelicOwnershipSyncService {
    private RelicOwnershipSyncService() {
    }

    public static void syncNonVampire(@Nonnull Ref<EntityStore> playerRef,
                                      @Nonnull Store<EntityStore> store) {
        RelicInventoryService.syncOwnership(playerRef, store, false);
    }

    public static void syncVampire(@Nonnull Ref<EntityStore> playerRef,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull Player player,
                                   @Nonnull BloodState state) {
        RelicInventoryService.SyncResult relicSync = RelicInventoryService.syncOwnership(
                playerRef,
                store,
                true,
                !RelicInventoryService.isAutoGrantSuppressed(playerRef));
        if (relicSync.inventoryFull()) {
            notifyInventoryFull(player, state, relicSync);
        } else {
            state.relicInventoryFullNotified = false;
        }
    }

    private static void notifyInventoryFull(@Nonnull Player player,
                                            @Nonnull BloodState state,
                                            @Nonnull RelicInventoryService.SyncResult relicSync) {
        if (state.relicInventoryFullNotified) {
            return;
        }
        String message = relicSync.firstStrandedLocation() != null
                ? "Your Vampirism Relic is stuck in " + relicSync.firstStrandedLocation().describe()
                + ". Free a visible inventory slot and use /vampirismrelic get."
                : "Your inventory is full, so your Vampirism Relic could not be returned. Free a visible inventory slot and use /vampirismrelic get.";
        player.sendMessage(Message.raw(message).color("yellow"));
        state.relicInventoryFullNotified = true;
    }
}
