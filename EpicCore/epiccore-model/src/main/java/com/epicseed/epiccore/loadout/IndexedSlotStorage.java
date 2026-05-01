package com.epicseed.epiccore.loadout;

public interface IndexedSlotStorage<T> {

    int capacity();

    T get(int slot);

    void set(int slot, T value);

    void remove(int slot);

    boolean canAdd(T value);

    void add(T value);
}
