package com.epicseed.vampirism.skill.data;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.skill.data.SkillLoaderModelAdapter;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.ModifierDef;
import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.model.StatDef;
import com.epicseed.epiccore.skill.model.StateDef;

public final class VampirismSkillLoaderModelAdapter
        implements SkillLoaderModelAdapter<Ability, ModifierDef, ReusableDef, StateDef, StatDef> {

    @Override
    public Ability newAbility() {
        return new Ability();
    }

    @Override
    public ModifierDef newModifierDef() {
        return new ModifierDef();
    }

    @Override
    public ReusableDef newReusableDef() {
        return new ReusableDef();
    }

    @Override
    public StateDef newState() {
        return new StateDef();
    }

    @Override
    public StatDef newStatDef() {
        return new StatDef();
    }

    @Override
    public StatType resolveStatType(String statId) {
        if (statId == null || statId.isBlank()) {
            return null;
        }
        try {
            return VampireStatType.valueOf(statId.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
