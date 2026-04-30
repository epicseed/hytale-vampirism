package com.epicseed.vampirism.skill.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public interface AbilityResourcePort extends com.epicseed.epiccore.skill.runtime.AbilityResourcePort<Ref<EntityStore>> {

    boolean canAffordBlood(Ref<EntityStore> casterRef, int bloodCost);

    void spendBlood(Ref<EntityStore> casterRef, int bloodCost);

    @Override
    default boolean canAfford(Ref<EntityStore> casterRef, int resourceCost) {
        return canAffordBlood(casterRef, resourceCost);
    }

    @Override
    default void spend(Ref<EntityStore> casterRef, int resourceCost) {
        spendBlood(casterRef, resourceCost);
    }
}
