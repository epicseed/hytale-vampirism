package com.epicseed.vampirism.skill.registry;

import com.epicseed.vampirism.skill.model.ReusableDef;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ReusableDefRegistry {

    private final Map<String, ReusableDef> map = new HashMap<>();

    public void Register(ReusableDef entry) {
        map.put(entry.id, entry);
    }

    public ReusableDef Get(String id) {
        return map.get(id);
    }

    public Collection<ReusableDef> GetAll() {
        return map.values();
    }
}
