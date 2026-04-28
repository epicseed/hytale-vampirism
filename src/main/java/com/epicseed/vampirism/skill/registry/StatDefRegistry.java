package com.epicseed.vampirism.skill.registry;

import com.epicseed.vampirism.skill.model.StatDef;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class StatDefRegistry {

    private final Map<String, StatDef> map = new HashMap<>();

    public void Register(StatDef entry) {
        map.put(entry.id, entry);
    }

    public StatDef Get(String id) {
        return map.get(id);
    }

    public Collection<StatDef> GetAll() {
        return map.values();
    }
}
