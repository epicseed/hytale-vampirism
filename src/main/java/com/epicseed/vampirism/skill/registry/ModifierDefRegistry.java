package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.vampirism.skill.model.ModifierDef;

public class ModifierDefRegistry extends IdRegistry<ModifierDef> {

    public ModifierDefRegistry() {
        super(entry -> entry.id);
    }

    public void Register(ModifierDef entry) {
        register(entry);
    }

    public ModifierDef Get(String id) {
        return get(id);
    }

    public java.util.Collection<ModifierDef> GetAll() {
        return getAll();
    }
}
