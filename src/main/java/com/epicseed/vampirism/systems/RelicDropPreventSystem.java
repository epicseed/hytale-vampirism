package com.epicseed.vampirism.systems;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.DropItemEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Prevents the VampirismRelic from being dropped by the player.
 * Listens to DropItemEvent.PlayerRequest (cancellable) and cancels it
 * when the item being dropped is the VampirismRelic.
 */
public class RelicDropPreventSystem extends EntityEventSystem<EntityStore, DropItemEvent.PlayerRequest> {

    private static final String RELIC_ITEM_ID = "VampirismRelic";

    public RelicDropPreventSystem() {
        super(DropItemEvent.PlayerRequest.class);
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
            @Nonnull DropItemEvent.PlayerRequest event) {

        @SuppressWarnings("unchecked")
        ComponentType<EntityStore, ? extends InventoryComponent> compType =
            (ComponentType<EntityStore, ? extends InventoryComponent>)
            InventoryComponent.getComponentTypeById(event.getInventorySectionId());

        if (compType == null) return;

        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        InventoryComponent inventory = (InventoryComponent) store.getComponent(ref, compType);
        if (inventory == null) return;

        ItemStack stack = inventory.getInventory().getItemStack(event.getSlotId());
        if (stack != null && RELIC_ITEM_ID.equals(stack.getItemId())) {
            event.setCancelled(true);
        }
    }
}
