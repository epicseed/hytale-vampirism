package com.epicseed.vampirism.modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry for all vampire stat modifiers.
 *
 * <p>Two modifier scopes exist:
 * <ul>
 *   <li><b>Global</b> — registered once at plugin startup, evaluated for every player</li>
 *   <li><b>Per-player</b> — registered and removed dynamically per UUID when skills are
 *       unlocked, effects are applied, or relic abilities are activated</li>
 * </ul>
 *
 * <p>{@link #compute} runs the global chain followed by the per-player chain, both in
 * ascending priority order, passing the output of each modifier as input to the next.
 *
 * <p>Thread safety: registration and eviction are thread-safe (called from async contexts
 * such as skill unlock commands). {@code compute} itself runs on the WorldThread.
 */
public final class ModifierRegistry {

    private static ModifierRegistry instance;

    /** Global modifiers — applied to every player, sorted by priority. */
    private final Map<StatType, List<ModifierEntry>> global = new HashMap<>();

    /** Per-player modifiers — keyed by UUID then StatType. */
    private final ConcurrentHashMap<UUID, Map<StatType, List<ModifierEntry>>> perPlayer
            = new ConcurrentHashMap<>();

    private static final Comparator<ModifierEntry> BY_PRIORITY =
            Comparator.comparingInt(ModifierEntry::priority);

    private ModifierRegistry() {}

    public static ModifierRegistry get() {
        if (instance == null) instance = new ModifierRegistry();
        return instance;
    }

    // -------------------------------------------------------------------------
    // Registration — global
    // -------------------------------------------------------------------------

    /**
     * Registers a global modifier that applies to every player.
     * Replaces any existing entry with the same {@code tag} for this {@code stat}.
     */
    public synchronized void registerGlobal(StatType stat, ModifierTag tag, int priority, StatModifier modifier) {
        List<ModifierEntry> list = global.computeIfAbsent(stat, k -> new ArrayList<>());
        list.removeIf(e -> e.tag().key().equals(tag.key()));
        list.add(new ModifierEntry(tag, priority, modifier));
        list.sort(BY_PRIORITY);
    }

    // -------------------------------------------------------------------------
    // Registration — per player
    // -------------------------------------------------------------------------

    /**
     * Registers a per-player modifier for {@code uuid}.
     * Replaces any existing entry with the same {@code tag} for this {@code stat}.
     */
    public void register(UUID uuid, StatType stat, ModifierTag tag, int priority, StatModifier modifier) {
        Map<StatType, List<ModifierEntry>> stats = perPlayer.computeIfAbsent(uuid, k -> new HashMap<>());
        synchronized (stats) {
            List<ModifierEntry> list = stats.computeIfAbsent(stat, k -> new ArrayList<>());
            list.removeIf(e -> e.tag().key().equals(tag.key()));
            list.add(new ModifierEntry(tag, priority, modifier));
            list.sort(BY_PRIORITY);
        }
    }

    /**
     * Removes the per-player modifier with the given {@code tag} for {@code stat}.
     * No-op if not registered.
     */
    public void unregister(UUID uuid, StatType stat, ModifierTag tag) {
        Map<StatType, List<ModifierEntry>> stats = perPlayer.get(uuid);
        if (stats == null) return;
        synchronized (stats) {
            List<ModifierEntry> list = stats.get(stat);
            if (list != null) list.removeIf(e -> e.tag().key().equals(tag.key()));
        }
    }

    /**
     * Removes all per-player modifiers whose tag starts with {@code prefix}.
     *
     * <p>Use {@code "skill:"} to clear all skill modifiers for a player on skill reset,
     * or {@code "effect:"} to clear all active-effect modifiers on death/cure.
     */
    public void unregisterByTagPrefix(UUID uuid, String prefix) {
        Map<StatType, List<ModifierEntry>> stats = perPlayer.get(uuid);
        if (stats == null) return;
        synchronized (stats) {
            for (List<ModifierEntry> list : stats.values()) {
                list.removeIf(e -> e.tag().key().startsWith(prefix));
            }
        }
    }

    /**
     * Removes all per-player modifiers for {@code uuid}.
     * Call this on player disconnect to prevent memory leaks.
     */
    public void evict(UUID uuid) {
        perPlayer.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // Evaluation
    // -------------------------------------------------------------------------

    /**
     * Evaluates the complete modifier pipeline for {@code stat} starting from {@code base}.
     *
     * <p>Order: global modifiers (by priority) → per-player modifiers (by priority).
     *
     * @param stat    the stat to compute
     * @param base    the starting value before any modifiers
     * @param ctx     player state snapshot for this evaluation
     * @return the final value after all modifiers have been applied
     */
    public float compute(StatType stat, float base, ModifierContext ctx) {
        float value = base;

        // Global chain
        List<ModifierEntry> globalList;
        synchronized (this) {
            List<ModifierEntry> raw = global.get(stat);
            globalList = raw != null ? new ArrayList<>(raw) : Collections.emptyList();
        }
        for (ModifierEntry e : globalList) {
            value = e.modifier().apply(value, ctx);
        }

        // Per-player chain
        Map<StatType, List<ModifierEntry>> stats = perPlayer.get(ctx.uuid());
        if (stats != null) {
            List<ModifierEntry> playerList;
            synchronized (stats) {
                List<ModifierEntry> raw = stats.get(stat);
                playerList = raw != null ? new ArrayList<>(raw) : Collections.emptyList();
            }
            for (ModifierEntry e : playerList) {
                value = e.modifier().apply(value, ctx);
            }
        }

        return value;
    }
}
