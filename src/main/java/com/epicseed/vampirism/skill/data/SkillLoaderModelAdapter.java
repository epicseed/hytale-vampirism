package com.epicseed.vampirism.skill.data;

import com.epicseed.vampirism.modifier.StatType;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.ModifierDef;
import com.epicseed.vampirism.skill.model.ReusableDef;
import com.epicseed.vampirism.skill.model.StatDef;
import com.epicseed.vampirism.skill.model.VampireState;

public interface SkillLoaderModelAdapter {

    Ability newAbility();

    ModifierDef newModifierDef();

    ReusableDef newReusableDef();

    VampireState newState();

    StatDef newStatDef();

    StatType resolveStatType(String statId);
}
