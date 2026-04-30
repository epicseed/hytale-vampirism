package com.epicseed.epiccore.registry;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Minimal reusable registry keyed by a stable string identifier.
 *
 * @param <T> entry type
 */
public class IdRegistry<T> {

    private final Map<String, T> map = new HashMap<>();
    private final Function<T, String> idExtractor;

    public IdRegistry(Function<T, String> idExtractor) {
        this.idExtractor = idExtractor;
    }

    public void register(T entry) {
        map.put(idExtractor.apply(entry), entry);
    }

    public T get(String id) {
        return map.get(id);
    }

    public Collection<T> getAll() {
        return map.values();
    }
}
