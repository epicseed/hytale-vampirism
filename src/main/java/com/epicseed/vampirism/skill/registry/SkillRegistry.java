package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.epiccore.skill.model.Skill;

/// Has the list that holds the Skill data
public class SkillRegistry extends IdRegistry<Skill> {

    public SkillRegistry() {
        super(skill -> skill.id);
    }

    public void Register(Skill skill){
        register(skill);
    }

    public Skill GetSkill(String id){
        return get(id);
    }

    public java.util.Collection<Skill> GetAll(){
        return getAll();
    }
}
