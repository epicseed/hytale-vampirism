package com.epicseed.epiccore.skill.runtime;

public interface AbilityResourcePort<TARGET> {
    boolean canAfford(TARGET target, int resourceCost);
    void spend(TARGET target, int resourceCost);
}
