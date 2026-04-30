package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.vampirism.skill.model.Ability;

public class AbilityRegistry extends IdRegistry<Ability> {

    public AbilityRegistry() {
        super(entry -> entry.id);
    }

    public void Register(Ability entry) {
        register(entry);
    }

    public Ability Get(String id) {
        return get(id);
    }

    public java.util.Collection<Ability> GetAll() {
        return getAll();
    }
}
