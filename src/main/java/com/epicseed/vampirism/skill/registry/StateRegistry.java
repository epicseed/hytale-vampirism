package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.vampirism.skill.model.VampireState;

public class StateRegistry extends IdRegistry<VampireState> {

    public StateRegistry() {
        super(entry -> entry.id);
    }

    public void Register(VampireState entry) {
        register(entry);
    }

    public VampireState Get(String id) {
        return get(id);
    }

    public java.util.Collection<VampireState> GetAll() {
        return getAll();
    }
}
