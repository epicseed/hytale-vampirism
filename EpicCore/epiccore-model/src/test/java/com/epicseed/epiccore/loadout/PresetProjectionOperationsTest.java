package com.epicseed.epiccore.loadout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

class PresetProjectionOperationsTest {

    @Test
    void projectSlotsStashesVisibleItemsAndAddsProxies() {
        InMemorySlotStorage storage = new InMemorySlotStorage(4);
        storage.set(0, "weapon");
        storage.set(1, "proxy-1");
        LinkedHashMap<Integer, String> stash = new LinkedHashMap<>();

        PresetProjectionOperations.projectSlots(
                storage,
                3,
                stash,
                value -> value == null,
                value -> value != null && value.startsWith("proxy-"),
                slot -> "proxy-" + slot);

        assertEquals("weapon", stash.get(0));
        assertEquals("proxy-0", storage.get(0));
        assertEquals("proxy-1", storage.get(1));
        assertEquals("proxy-2", storage.get(2));
        assertNull(storage.get(3));
    }

    @Test
    void restoreSlotsPutsStashedItemsBackAndReturnsLeftovers() {
        InMemorySlotStorage storage = new InMemorySlotStorage(3);
        storage.set(0, "proxy-0");
        storage.set(1, "occupied");
        storage.set(2, "proxy-2");
        LinkedHashMap<Integer, String> stash = new LinkedHashMap<>();
        stash.put(0, "weapon");
        stash.put(1, "ring");
        stash.put(2, "cloak");
        List<String> returned = new ArrayList<>();

        List<String> failedReturns = PresetProjectionOperations.restoreSlots(
                storage,
                stash,
                value -> value == null,
                value -> value != null && value.startsWith("proxy-"),
                value -> returned.add(value));

        assertEquals("weapon", storage.get(0));
        assertEquals("occupied", storage.get(1));
        assertEquals("cloak", storage.get(2));
        assertEquals(List.of("ring"), returned);
        assertTrue(failedReturns.isEmpty());
        assertTrue(stash.isEmpty());
    }

    @Test
    void removeMatchingRemovesOnlyMatchingEntries() {
        InMemorySlotStorage storage = new InMemorySlotStorage(3);
        storage.set(0, "proxy-0");
        storage.set(1, "real-item");
        storage.set(2, "proxy-2");

        PresetProjectionOperations.removeMatching(storage, value -> value != null && value.startsWith("proxy-"));

        assertNull(storage.get(0));
        assertEquals("real-item", storage.get(1));
        assertNull(storage.get(2));
    }

    private static final class InMemorySlotStorage implements IndexedSlotStorage<String> {
        private final List<String> slots;

        private InMemorySlotStorage(int capacity) {
            this.slots = new ArrayList<>(java.util.Collections.nCopies(capacity, null));
        }

        @Override
        public int capacity() {
            return slots.size();
        }

        @Override
        public String get(int slot) {
            return slots.get(slot);
        }

        @Override
        public void set(int slot, String value) {
            slots.set(slot, value);
        }

        @Override
        public void remove(int slot) {
            slots.set(slot, null);
        }

        @Override
        public boolean canAdd(String value) {
            return slots.contains(null);
        }

        @Override
        public void add(String value) {
            int slot = slots.indexOf(null);
            if (slot >= 0) {
                slots.set(slot, value);
            }
        }
    }
}
