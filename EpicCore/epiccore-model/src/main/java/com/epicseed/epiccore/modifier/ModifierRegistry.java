package com.epicseed.epiccore.modifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generic registry for stat modifier chains keyed by subject UUID and stat type.
 *
 * @param <C> the evaluation context type
 */
public class ModifierRegistry<C extends ModifierSubject> {

    private final Map<StatType, List<ModifierEntry<C>>> global = new HashMap<>();
    private final ConcurrentHashMap<UUID, Map<StatType, List<ModifierEntry<C>>>> perPlayer
            = new ConcurrentHashMap<>();

    public synchronized void registerGlobal(StatType stat, ModifierTag tag, int priority, ValueModifier<C> modifier) {
        List<ModifierEntry<C>> list = global.computeIfAbsent(stat, ignored -> new ArrayList<>());
        list.removeIf(entry -> entry.tag().key().equals(tag.key()));
        list.add(new ModifierEntry<>(tag, priority, modifier));
        sortEntries(list);
    }

    public void register(UUID uuid, StatType stat, ModifierTag tag, int priority, ValueModifier<C> modifier) {
        Map<StatType, List<ModifierEntry<C>>> stats = perPlayer.computeIfAbsent(uuid, ignored -> new HashMap<>());
        synchronized (stats) {
            List<ModifierEntry<C>> list = stats.computeIfAbsent(stat, ignored -> new ArrayList<>());
            list.removeIf(entry -> entry.tag().key().equals(tag.key()));
            list.add(new ModifierEntry<>(tag, priority, modifier));
            sortEntries(list);
        }
    }

    public void unregister(UUID uuid, StatType stat, ModifierTag tag) {
        Map<StatType, List<ModifierEntry<C>>> stats = perPlayer.get(uuid);
        if (stats == null) {
            return;
        }
        synchronized (stats) {
            List<ModifierEntry<C>> list = stats.get(stat);
            if (list != null) {
                list.removeIf(entry -> entry.tag().key().equals(tag.key()));
            }
        }
    }

    public void unregisterByTagPrefix(UUID uuid, String prefix) {
        Map<StatType, List<ModifierEntry<C>>> stats = perPlayer.get(uuid);
        if (stats == null) {
            return;
        }
        synchronized (stats) {
            for (List<ModifierEntry<C>> list : stats.values()) {
                list.removeIf(entry -> entry.tag().key().startsWith(prefix));
            }
        }
    }

    public void evict(UUID uuid) {
        perPlayer.remove(uuid);
    }

    public float compute(StatType stat, float base, C ctx) {
        float value = base;

        List<ModifierEntry<C>> globalList;
        synchronized (this) {
            List<ModifierEntry<C>> raw = global.get(stat);
            globalList = raw != null ? new ArrayList<>(raw) : Collections.emptyList();
        }
        for (ModifierEntry<C> entry : globalList) {
            value = entry.modifier().apply(value, ctx);
        }

        Map<StatType, List<ModifierEntry<C>>> stats = perPlayer.get(ctx.uuid());
        if (stats == null) {
            return value;
        }

        List<ModifierEntry<C>> playerList;
        synchronized (stats) {
            List<ModifierEntry<C>> raw = stats.get(stat);
            playerList = raw != null ? new ArrayList<>(raw) : Collections.emptyList();
        }
        for (ModifierEntry<C> entry : playerList) {
            value = entry.modifier().apply(value, ctx);
        }

        return value;
    }

    private static <C> void sortEntries(List<ModifierEntry<C>> entries) {
        entries.sort((left, right) -> Integer.compare(left.priority(), right.priority()));
    }
}
