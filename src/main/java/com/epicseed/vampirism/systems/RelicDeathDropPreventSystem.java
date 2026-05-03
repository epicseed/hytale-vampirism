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
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Prevents the VampirismRelic from being dropped on player death.
 * DropItemEvent.Drop fires after the item is removed from the inventory slot,
 * so we cancel the world-spawn AND immediately add the item back.
 */
public class RelicDeathDropPreventSystem extends EntityEventSystem<EntityStore, DropItemEvent.Drop> {

    private static final int[] SECTION_IDS = { -1, -2, -8, -5 }; // hotbar, storage, tools, utility

    public RelicDeathDropPreventSystem() {
        super(DropItemEvent.Drop.class);
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
            @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
            @Nonnull Store<EntityStore> store,
            @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull DropItemEvent.Drop event) {

        ItemStack stack = event.getItemStack();
        if (stack == null || !RelicInventoryService.itemId().equals(stack.getItemId())) return;

        event.setCancelled(true);

        // Drop fires after the item is removed from the slot — add it back
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        for (int sectionId : SECTION_IDS) {
            InventoryComponent section = InventoryAdapter.getInventorySection(ref, store, sectionId);
            if (section == null) continue;
            ItemContainer container = section.getInventory();
            if (container.canAddItemStack(stack)) {
                container.addItemStack(stack);
                return;
            }
        }
    }
}
