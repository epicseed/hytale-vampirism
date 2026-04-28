package com.epicseed.vampirism.skill.registry;

import com.epicseed.vampirism.skill.model.EffectDef;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class EffectDefRegistry {

    private final Map<String, EffectDef> map = new HashMap<>();

    public void Register(EffectDef entry) {
        map.put(entry.id, entry);
    }

    public EffectDef Get(String id) {
        return map.get(id);
    }

    public Collection<EffectDef> GetAll() {
        return map.values();
    }
}
