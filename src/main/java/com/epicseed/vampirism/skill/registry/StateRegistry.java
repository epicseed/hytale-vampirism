package com.epicseed.vampirism.skill.registry;

import com.epicseed.vampirism.skill.model.VampireState;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class StateRegistry {

    private final Map<String, VampireState> map = new HashMap<>();

    public void Register(VampireState entry) {
        map.put(entry.id, entry);
    }

    public VampireState Get(String id) {
        return map.get(id);
    }

    public Collection<VampireState> GetAll() {
        return map.values();
    }
}
