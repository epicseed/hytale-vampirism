package com.epicseed.vampirism.skill.data;

import com.epicseed.vampirism.modifier.StatType;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.ModifierDef;
import com.epicseed.vampirism.skill.model.ReusableDef;
import com.epicseed.vampirism.skill.model.StatDef;
import com.epicseed.vampirism.skill.model.VampireState;

public final class VampirismSkillLoaderModelAdapter implements SkillLoaderModelAdapter {

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
    public VampireState newState() {
        return new VampireState();
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
