package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampireVitalityAbilityResourcePort implements AbilityResourcePort {

    @Override
    public boolean canAffordBlood(Ref<EntityStore> casterRef, int bloodCost) {
        return VampireVitalitySystem.canAffordBlood(casterRef, bloodCost);
    }

    @Override
    public void spendBlood(Ref<EntityStore> casterRef, int bloodCost) {
        VampireVitalitySystem.spendBlood(casterRef, bloodCost);
    }
}
