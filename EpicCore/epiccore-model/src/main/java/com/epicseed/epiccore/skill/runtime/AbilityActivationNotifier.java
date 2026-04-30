package com.epicseed.epiccore.skill.runtime;

@FunctionalInterface
public interface AbilityActivationNotifier<CTX> {
    void onActivated(CTX context, String abilityId);
}
