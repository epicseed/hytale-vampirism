package com.epicseed.epiccore.skill.data;

import java.util.Map;

public interface SkillDataLoadHooks {

    default void applyStateEffectBindings(Map<String, String> stateEffectBindings) {
    }

    default void applyAbilitySlotBindings(Map<String, String> abilitySlotBindings) {
    }

    static SkillDataLoadHooks noop() {
        return new SkillDataLoadHooks() {
        };
    }
}
