package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.modifier.VampireStatType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic per-player temporary stat modifier tracker.
 *
 * <p>Backs the JSON-driven {@code grantTemporaryModifier} action primitive in
 * {@link SkillActionExecutor}.  Each entry is a (stat, amount/multiplier, expiry) tuple stored
 * per UUID.  Consumers (e.g. {@code VampireMovementSystem}) read the current aggregate via
 * {@link #sumAdditive(UUID, VampireStatType)} or {@link #productMultiplicative(UUID, VampireStatType)}
 * once per tick.
 *
 * <p>Stacking policies:
 * <ul>
 *   <li>{@code REPLACE} – drop any existing entry for the same stat before adding.</li>
 *   <li>{@code REFRESH} – replace if the new expiry is later than the current max.</li>
 *   <li>{@code STACK}   – append, every entry counts until its own expiry.</li>
 * </ul>
 *
 * <p>Thread-safe; may be written from trigger-dispatch threads and read from the WorldThread.
 */
public final class TemporaryModifierTracker {

    public enum Stacking { REPLACE, REFRESH, STACK }

    public enum Op { ADDITIVE, MULTIPLICATIVE }

    private static final class Entry {
        final Op op;
        final float amount;
        long expiryMs;

        Entry(Op op, float amount, long expiryMs) {
            this.op = op;
            this.amount = amount;
            this.expiryMs = expiryMs;
        }
    }

    private static final Map<UUID, Map<VampireStatType, List<Entry>>> STATE = new ConcurrentHashMap<>();

    private TemporaryModifierTracker() {}

    // -------------------------------------------------------------------------
    // Mutation
    // -------------------------------------------------------------------------

    public static void addBoost(@Nonnull UUID uuid, @Nonnull VampireStatType stat,
                                float amount, float durationSeconds,
                                @Nonnull Stacking stacking, @Nonnull Op op) {
        long expiry = System.currentTimeMillis() + (long)(durationSeconds * 1000L);
        Map<VampireStatType, List<Entry>> perStat = STATE.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        synchronized (perStat) {
            List<Entry> list = perStat.computeIfAbsent(stat, k -> new ArrayList<>());
            switch (stacking) {
                case REPLACE -> {
                    list.clear();
                    list.add(new Entry(op, amount, expiry));
                }
                case REFRESH -> {
                    if (list.isEmpty()) {
                        list.add(new Entry(op, amount, expiry));
                    } else {
                        Entry existing = list.get(0);
                        if (existing.op == op && Math.abs(existing.amount - amount) < 1e-4f) {
                            existing.expiryMs = Math.max(existing.expiryMs, expiry);
                        } else {
                            list.clear();
                            list.add(new Entry(op, amount, expiry));
                        }
                    }
                }
                case STACK -> list.add(new Entry(op, amount, expiry));
            }
        }
    }

    /** Backwards-compatible shortcut: additive boost with REPLACE semantics (legacy speed boost). */
    public static void addBoost(@Nonnull UUID uuid, @Nonnull VampireStatType stat,
                                float amount, float durationSeconds) {
        addBoost(uuid, stat, amount, durationSeconds, Stacking.REPLACE, Op.ADDITIVE);
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        STATE.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    /** Returns the sum of all active additive entries for (uuid, stat); 0f when none. */
    public static float sumAdditive(@Nullable UUID uuid, @Nonnull VampireStatType stat) {
        if (uuid == null) return 0f;
        List<Entry> entries = snapshot(uuid, stat);
        if (entries.isEmpty()) return 0f;
        float total = 0f;
        for (Entry e : entries) {
            if (e.op == Op.ADDITIVE) total += e.amount;
        }
        return total;
    }

    /** Returns the product of all active multiplicative entries for (uuid, stat); 1f when none. */
    public static float productMultiplicative(@Nullable UUID uuid, @Nonnull VampireStatType stat) {
        if (uuid == null) return 1f;
        List<Entry> entries = snapshot(uuid, stat);
        if (entries.isEmpty()) return 1f;
        float total = 1f;
        for (Entry e : entries) {
            if (e.op == Op.MULTIPLICATIVE) total *= e.amount;
        }
        return total;
    }

    /** Legacy: active additive speed boost; kept for backward compatibility with old callers. */
    public static float getBoost(@Nullable UUID uuid) {
        return sumAdditive(uuid, VampireStatType.SPEED);
    }

    public static boolean hasBoost(@Nullable UUID uuid) {
        return uuid != null && sumAdditive(uuid, VampireStatType.SPEED) > 0f;
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private static List<Entry> snapshot(@Nonnull UUID uuid, @Nonnull VampireStatType stat) {
        Map<VampireStatType, List<Entry>> perStat = STATE.get(uuid);
        if (perStat == null) return List.of();
        List<Entry> list = perStat.get(stat);
        if (list == null) return List.of();
        long now = System.currentTimeMillis();
        synchronized (perStat) {
            Iterator<Entry> it = list.iterator();
            while (it.hasNext()) {
                if (it.next().expiryMs <= now) it.remove();
            }
            if (list.isEmpty()) {
                perStat.remove(stat);
                return List.of();
            }
            return new ArrayList<>(list);
        }
    }
}
