package com.epicseed.epiccore.skill.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AbilityCooldownTracker {

    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private AbilityCooldownTracker() {
    }

    public static boolean tryUse(UUID uuid, String abilityId, long cooldownMs) {
        if (cooldownMs <= 0L) {
            return true;
        }

        String key = uuid + ":" + abilityId;
        long now = System.nanoTime();
        long nextExpiry = now + TimeUnit.MILLISECONDS.toNanos(cooldownMs);
        AtomicBoolean reserved = new AtomicBoolean(false);

        cooldowns.compute(key, (ignored, expiry) -> {
            if (expiry != null && now < expiry) {
                return expiry;
            }
            reserved.set(true);
            return nextExpiry;
        });

        return reserved.get();
    }

    public static long getRemainingMs(UUID uuid, String abilityId) {
        Long expiry = cooldowns.get(uuid + ":" + abilityId);
        if (expiry == null) {
            return 0L;
        }
        long remainingNanos = Math.max(0L, expiry - System.nanoTime());
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos);
    }

    public static boolean isOnCooldown(UUID uuid, String abilityId) {
        return getRemainingMs(uuid, abilityId) > 0L;
    }

    public static Map<String, Long> snapshotRemaining(UUID uuid) {
        String prefix = uuid + ":";
        LinkedHashMap<String, Long> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix)) {
                continue;
            }
            long remainingNanos = Math.max(0L, entry.getValue() - System.nanoTime());
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            if (remainingMs > 0L) {
                snapshot.put(key.substring(prefix.length()), remainingMs);
            }
        }
        return snapshot;
    }

    public static void restorePlayer(UUID uuid, Map<String, Long> snapshot) {
        clearPlayer(uuid);
        if (snapshot.isEmpty()) {
            return;
        }

        long now = System.nanoTime();
        String prefix = uuid + ":";
        snapshot.forEach((abilityId, remainingMs) -> {
            if (abilityId == null || abilityId.isBlank() || remainingMs == null || remainingMs <= 0L) {
                return;
            }
            cooldowns.put(prefix + abilityId, now + TimeUnit.MILLISECONDS.toNanos(remainingMs));
        });
    }

    public static void reset(UUID uuid, String abilityId) {
        cooldowns.remove(uuid + ":" + abilityId);
    }

    public static void clearPlayer(UUID uuid) {
        String prefix = uuid + ":";
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
