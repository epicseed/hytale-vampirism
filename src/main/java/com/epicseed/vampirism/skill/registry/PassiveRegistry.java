package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.epiccore.skill.model.Passive;

public class PassiveRegistry extends IdRegistry<Passive> {

    public PassiveRegistry() {
        super(entry -> entry.id);
    }

    public void Register(Passive entry) {
        register(entry);
    }

    public Passive Get(String id) {
        return get(id);
    }

    public java.util.Collection<Passive> GetAll() {
        return getAll();
    }
}
