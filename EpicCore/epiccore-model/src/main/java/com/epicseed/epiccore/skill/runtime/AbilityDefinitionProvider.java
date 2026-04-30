package com.epicseed.epiccore.skill.runtime;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.Skill;

public interface AbilityDefinitionProvider {

    Ability getAbility(String id);

    Skill getSkill(String id);

    EffectDef getEffect(String id);
}
