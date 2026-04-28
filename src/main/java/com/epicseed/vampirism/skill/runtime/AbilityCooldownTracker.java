package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nonnull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-player, per-ability cooldown tracker.
 *
 * <p>Cooldowns are stored as monotonic expiry timestamps in nanoseconds.
 * Using {@link System#nanoTime()} avoids countdown jumps when the system clock changes.
 * The tracker is thread-safe and stores only in-memory state — data does not survive a restart.
 * Evict player state on disconnect via {@link #clearPlayer(UUID)}.
 */
public final class AbilityCooldownTracker {

    private static final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    private AbilityCooldownTracker() {}

    /**
     * Attempts to activate an ability for a player.
     *
     * @param uuid        the player UUID
     * @param abilityId   the ability identifier
     * @param cooldownMs  the cooldown duration in milliseconds; {@code 0} means no cooldown
     * @return {@code true} if the ability was available and is now put on cooldown,
     *         {@code false} if still on cooldown
     */
    public static boolean tryUse(@Nonnull UUID uuid, @Nonnull String abilityId, long cooldownMs) {
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

    /**
     * Returns the remaining cooldown in milliseconds, or {@code 0L} if the ability is ready.
     */
    public static long getRemainingMs(@Nonnull UUID uuid, @Nonnull String abilityId) {
        Long expiry = cooldowns.get(uuid + ":" + abilityId);
        if (expiry == null) return 0L;
        long remainingNanos = Math.max(0L, expiry - System.nanoTime());
        return TimeUnit.NANOSECONDS.toMillis(remainingNanos);
    }

    /** Returns {@code true} if the ability is currently on cooldown for this player. */
    public static boolean isOnCooldown(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return getRemainingMs(uuid, abilityId) > 0L;
    }

    /**
     * Returns a snapshot of all active cooldowns for a player as remaining milliseconds.
     * Expired entries are omitted.
     */
    @Nonnull
    public static Map<String, Long> snapshotRemaining(@Nonnull UUID uuid) {
        String prefix = uuid + ":";
        LinkedHashMap<String, Long> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Long> entry : cooldowns.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(prefix)) continue;
            long remainingNanos = Math.max(0L, entry.getValue() - System.nanoTime());
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(remainingNanos);
            if (remainingMs > 0L) {
                snapshot.put(key.substring(prefix.length()), remainingMs);
            }
        }
        return snapshot;
    }

    /**
     * Restores a previously captured per-ability cooldown snapshot for a player.
     * Values are interpreted as remaining milliseconds from "now".
     */
    public static void restorePlayer(@Nonnull UUID uuid, @Nonnull Map<String, Long> snapshot) {
        clearPlayer(uuid);
        if (snapshot.isEmpty()) return;

        long now = System.nanoTime();
        String prefix = uuid + ":";
        snapshot.forEach((abilityId, remainingMs) -> {
            if (abilityId == null || abilityId.isBlank() || remainingMs == null || remainingMs <= 0L) {
                return;
            }
            cooldowns.put(prefix + abilityId, now + TimeUnit.MILLISECONDS.toNanos(remainingMs));
        });
    }

    /**
     * Resets the cooldown for a specific ability, making it immediately usable again.
     */
    public static void reset(@Nonnull UUID uuid, @Nonnull String abilityId) {
        cooldowns.remove(uuid + ":" + abilityId);
    }

    /**
     * Removes all cooldown entries for a player. Call this on player disconnect to avoid
     * unbounded memory growth.
     */
    public static void clearPlayer(@Nonnull UUID uuid) {
        String prefix = uuid + ":";
        cooldowns.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
