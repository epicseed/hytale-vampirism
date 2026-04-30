package com.epicseed.epiccore.skill.runtime;

import java.util.UUID;

public interface AbilityRuntimeContext<T, SELF extends AbilityRuntimeContext<T, SELF>> {

    UUID uuid();

    T ref();

    T targetRef();

    String currentAbilityId();

    SELF withTarget(T newTargetRef);

    int activationDepth();

    boolean hasAbilityInActivationPath(String abilityId);

    String activationPathString();

    SELF withActivatedAbility(String abilityId);
}
