package com.epicseed.epiccore.loadout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public final class PresetProjectionOperations {

    private PresetProjectionOperations() {
    }

    public static <T> void projectSlots(IndexedSlotStorage<T> utility,
                                        int slotCount,
                                        LinkedHashMap<Integer, T> stash,
                                        Predicate<T> isEmpty,
                                        Predicate<T> isProxy,
                                        IntFunction<T> createProxy) {
        int clampedSlotCount = Math.max(0, Math.min(slotCount, utility.capacity()));
        for (int slot = 0; slot < clampedSlotCount; slot++) {
            T current = utility.get(slot);
            if (!isEmpty.test(current) && !isProxy.test(current)) {
                stash.putIfAbsent(slot, current);
            }
            if (isProxy.test(current)) {
                continue;
            }
            utility.set(slot, createProxy.apply(slot));
        }
    }

    public static <T> List<T> restoreSlots(IndexedSlotStorage<T> utility,
                                           LinkedHashMap<Integer, T> stash,
                                           Predicate<T> isEmpty,
                                           Predicate<T> isProxy,
                                           Predicate<T> returnToVisibleInventory) {
        List<T> failedReturns = new ArrayList<>();
        for (int slot = 0; slot < utility.capacity(); slot++) {
            T current = utility.get(slot);
            T restore = stash.remove(slot);
            if (restore != null) {
                if (isEmpty.test(current) || isProxy.test(current)) {
                    utility.set(slot, restore);
                } else {
                    if (!returnToVisibleInventory.test(restore)) {
                        failedReturns.add(restore);
                    }
                }
                continue;
            }
            if (isProxy.test(current)) {
                utility.remove(slot);
            }
        }

        for (Map.Entry<Integer, T> leftover : stash.entrySet()) {
            if (!returnToVisibleInventory.test(leftover.getValue())) {
                failedReturns.add(leftover.getValue());
            }
        }
        stash.clear();
        return failedReturns;
    }

    public static <T> void removeMatching(IndexedSlotStorage<T> storage, Predicate<T> matcher) {
        for (int slot = 0; slot < storage.capacity(); slot++) {
            T current = storage.get(slot);
            if (matcher.test(current)) {
                storage.remove(slot);
            }
        }
    }
}
