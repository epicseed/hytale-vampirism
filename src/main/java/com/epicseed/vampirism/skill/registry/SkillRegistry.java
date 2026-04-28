package com.epicseed.vampirism.skill.registry;

import com.epicseed.vampirism.skill.model.Skill;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/// Has the list that holds the Skill data
public class SkillRegistry {

    private final Map<String, Skill> skillHashMap = new HashMap<>();

    public void Register(Skill skill){

        skillHashMap.put(skill.id, skill);
    }

    public Skill GetSkill(String id){

        return skillHashMap.get(id);
    }

//    public Skill GetSkillAt(Position position){
//
//        for(Skill skill : skillHashMap.values()){
//
//            if(position == skill.position){
//                return skill;
//            }
//        }
//
//        return
//    }

    public Collection<Skill> GetAll(){

        return skillHashMap.values();
    }
}
