package com.epicseed.epiccore.loadout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class GrantSuppressionTracker<K> {

    private final Map<K, Long> suppressedUntil = new ConcurrentHashMap<>();

    public void suppress(K key, long durationMs) {
        if (key == null || durationMs <= 0L) {
            return;
        }
        suppressedUntil.put(key, System.currentTimeMillis() + durationMs);
    }

    public boolean isSuppressed(K key) {
        if (key == null) {
            return false;
        }
        Long until = suppressedUntil.get(key);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            suppressedUntil.remove(key, until);
            return false;
        }
        return true;
    }

    public void clear(K key) {
        if (key != null) {
            suppressedUntil.remove(key);
        }
    }
}
