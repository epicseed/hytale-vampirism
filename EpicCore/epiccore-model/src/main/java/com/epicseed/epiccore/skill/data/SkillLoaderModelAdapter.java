package com.epicseed.epiccore.skill.data;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.ModifierDef;
import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.model.StateDef;

public interface SkillLoaderModelAdapter<A extends Ability,
        M extends ModifierDef,
        R extends ReusableDef,
        S extends StateDef,
        T extends com.epicseed.epiccore.skill.model.StatDef> {

    A newAbility();

    M newModifierDef();

    R newReusableDef();

    S newState();

    T newStatDef();

    StatType resolveStatType(String statId);
}
