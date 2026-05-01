package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.epiccore.skill.model.ReusableDef;

public class ReusableDefRegistry extends IdRegistry<ReusableDef> {

    public ReusableDefRegistry() {
        super(entry -> entry.id);
    }

    public void Register(ReusableDef entry) {
        register(entry);
    }

    public ReusableDef Get(String id) {
        return get(id);
    }

    public java.util.Collection<ReusableDef> GetAll() {
        return getAll();
    }
}
