package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.epiccore.skill.model.StateDef;

public class StateRegistry extends IdRegistry<StateDef> {

    public StateRegistry() {
        super(entry -> entry.id);
    }

    public void Register(StateDef entry) {
        register(entry);
    }

    public StateDef Get(String id) {
        return get(id);
    }

    public java.util.Collection<StateDef> GetAll() {
        return getAll();
    }
}
