package com.epicseed.epiccore.loadout.hytale;

import com.epicseed.epiccore.loadout.IndexedSlotStorage;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

public final class ItemContainerSlotStorage implements IndexedSlotStorage<ItemStack> {

    private final ItemContainer container;

    public ItemContainerSlotStorage(ItemContainer container) {
        this.container = container;
    }

    @Override
    public int capacity() {
        return container.getCapacity();
    }

    @Override
    public ItemStack get(int slot) {
        return container.getItemStack((short) slot);
    }

    @Override
    public void set(int slot, ItemStack value) {
        container.setItemStackForSlot((short) slot, value);
    }

    @Override
    public void remove(int slot) {
        container.removeItemStackFromSlot((short) slot);
    }

    @Override
    public boolean canAdd(ItemStack value) {
        return container.canAddItemStack(value);
    }

    @Override
    public void add(ItemStack value) {
        container.addItemStack(value);
    }
}
