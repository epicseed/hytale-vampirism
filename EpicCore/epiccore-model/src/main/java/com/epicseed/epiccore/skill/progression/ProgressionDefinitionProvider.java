package com.epicseed.epiccore.skill.progression;

import java.util.Collection;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.AbilityDefinitionProvider;

public interface ProgressionDefinitionProvider extends AbilityDefinitionProvider {

    Collection<Skill> getAllSkills();

    Passive getPassive(String id);

    default Skill findSkillByAbilityId(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }
        for (Skill skill : getAllSkills()) {
            if (abilityId.equals(skill.abilityId)) {
                return skill;
            }
        }
        return null;
    }

    @Override
    Ability getAbility(String id);

    @Override
    Skill getSkill(String id);
}
