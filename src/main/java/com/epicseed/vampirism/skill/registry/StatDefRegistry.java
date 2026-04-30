package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.vampirism.skill.model.StatDef;

public class StatDefRegistry extends IdRegistry<StatDef> {

    public StatDefRegistry() {
        super(entry -> entry.id);
    }

    public void Register(StatDef entry) {
        register(entry);
    }

    public StatDef Get(String id) {
        return get(id);
    }

    public java.util.Collection<StatDef> GetAll() {
        return getAll();
    }
}
