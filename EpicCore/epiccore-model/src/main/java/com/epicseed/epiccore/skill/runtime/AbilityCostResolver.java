package com.epicseed.epiccore.skill.runtime;

import com.epicseed.epiccore.skill.model.Ability;

@FunctionalInterface
public interface AbilityCostResolver<CTX> {
    AbilityActivationCharge resolveCharge(Ability ability, CTX context);
}
