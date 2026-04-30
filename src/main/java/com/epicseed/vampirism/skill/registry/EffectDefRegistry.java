package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.epiccore.skill.model.EffectDef;

public class EffectDefRegistry extends IdRegistry<EffectDef> {

    public EffectDefRegistry() {
        super(entry -> entry.id);
    }

    public void Register(EffectDef entry) {
        register(entry);
    }

    public EffectDef Get(String id) {
        return get(id);
    }

    public java.util.Collection<EffectDef> GetAll() {
        return getAll();
    }
}
