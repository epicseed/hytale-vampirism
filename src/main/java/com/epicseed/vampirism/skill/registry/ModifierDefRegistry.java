package com.epicseed.vampirism.skill.registry;

import com.epicseed.vampirism.skill.model.ModifierDef;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ModifierDefRegistry {

    private final Map<String, ModifierDef> map = new HashMap<>();

    public void Register(ModifierDef entry) {
        map.put(entry.id, entry);
    }

    public ModifierDef Get(String id) {
        return map.get(id);
    }

    public Collection<ModifierDef> GetAll() {
        return map.values();
    }
}
