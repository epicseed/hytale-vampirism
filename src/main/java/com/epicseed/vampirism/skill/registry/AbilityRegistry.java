package com.epicseed.vampirism.skill.registry;

import com.epicseed.vampirism.skill.model.Ability;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AbilityRegistry {

    private final Map<String, Ability> map = new HashMap<>();

    public void Register(Ability entry) {
        map.put(entry.id, entry);
    }

    public Ability Get(String id) {
        return map.get(id);
    }

    public Collection<Ability> GetAll() {
        return map.values();
    }
}
