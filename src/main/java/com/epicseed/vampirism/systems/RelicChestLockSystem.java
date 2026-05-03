package com.epicseed.vampirism.systems;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.InventoryAdapter;
import com.epicseed.epiccore.relic.application.RelicInventoryService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.inventory.InventoryChangeEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackSlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.ListTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.MoveType;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.hypixel.hytale.server.core.inventory.transaction.Transaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Prevents the VampirismRelic from being moved to external containers (chests, etc.)
 * by reversing any MOVE_FROM_SELF transaction where the destination is not a player section.
 */
public class RelicChestLockSystem extends EntityEventSystem<EntityStore, InventoryChangeEvent> {

    private static final int[] PLAYER_SECTION_IDS = { -1, -2, -3, -5, -8, -9 }; // hotbar, storage, armor, utility, tools, backpack
    private static final long AUTO_GRANT_SUPPRESSION_MS = 750L;

    public RelicChestLockSystem() {
        super(InventoryChangeEvent.class);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(
            int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull InventoryChangeEvent event) {

        Ref<EntityStore> ref = chunk.getReferenceTo(index);
        Transaction txn = event.getTransaction();

        // ListTransaction wraps multiple moves (e.g. quickStack / putAll)
        if (txn instanceof ListTransaction<?> list && list.succeeded()) {
            for (Transaction t : list.getList()) {
                handleMove(t, event.getItemContainer(), store, ref);
            }
            return;
        }

        handleMove(txn, event.getItemContainer(), store, ref);
    }

    @SuppressWarnings("unchecked")
    private void handleMove(
            Transaction txn,
            ItemContainer source,
            Store<EntityStore> store,
            Ref<EntityStore> ref) {

        if (!(txn instanceof MoveTransaction<?> move)) return;
        if (!move.succeeded()) return;
        if (move.getMoveType() != MoveType.MOVE_FROM_SELF) return;

        SlotTransaction removeT = move.getRemoveTransaction();
        ItemStack removedItem = removeT.getSlotBefore();
        if (ItemStack.isEmpty(removedItem)) return;
        if (!RelicInventoryService.itemId().equals(removedItem.getItemId())) return;

        // Allow internal moves (hotbar↔storage, etc.) by checking if dest is a player section
        ItemContainer dest = move.getOtherContainer();
        if (InventoryAdapter.isPlayerSectionContainer(ref, store, dest, PLAYER_SECTION_IDS)) {
            return;
        }

        // relic moved to an external container — reverse the move
        RelicInventoryService.suppressAutoGrant(ref, AUTO_GRANT_SUPPRESSION_MS);
        Transaction addTxn = move.getAddTransaction();

        if (addTxn instanceof SlotTransaction addT && addT.succeeded()) {
            dest.moveItemStackFromSlotToSlot(
                    addT.getSlot(), removedItem.getQuantity(), source, removeT.getSlot(), false);
        } else if (addTxn instanceof ItemStackTransaction ita && ita.succeeded()) {
            for (ItemStackSlotTransaction slotTxn : ita.getSlotTransactions()) {
                if (!slotTxn.succeeded()) continue;
                dest.moveItemStackFromSlotToSlot(
                        slotTxn.getSlot(), removedItem.getQuantity(), source, removeT.getSlot(), false);
            }
        }
    }
}
