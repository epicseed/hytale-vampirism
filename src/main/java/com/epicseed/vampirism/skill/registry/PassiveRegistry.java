package com.epicseed.vampirism.skill.registry;

import com.epicseed.vampirism.skill.model.Passive;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class PassiveRegistry {

    private final Map<String, Passive> map = new HashMap<>();

    public void Register(Passive entry) {
        map.put(entry.id, entry);
    }

    public Passive Get(String id) {
        return map.get(id);
    }

    public Collection<Passive> GetAll() {
        return map.values();
    }
}
