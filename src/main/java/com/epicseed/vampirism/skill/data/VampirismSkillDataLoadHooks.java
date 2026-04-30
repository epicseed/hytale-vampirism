package com.epicseed.vampirism.skill.data;

import com.epicseed.epiccore.skill.data.SkillDataLoadHooks;
import com.epicseed.epiccore.skill.runtime.AbilitySlotBindings;
import com.epicseed.epiccore.skill.runtime.StateEffectBindings;

import java.util.Map;

public final class VampirismSkillDataLoadHooks implements SkillDataLoadHooks {

    @Override
    public void applyStateEffectBindings(Map<String, String> stateEffectBindings) {
        StateEffectBindings.set(stateEffectBindings);
    }

    @Override
    public void applyAbilitySlotBindings(Map<String, String> abilitySlotBindings) {
        AbilitySlotBindings.set(abilitySlotBindings);
    }
}
