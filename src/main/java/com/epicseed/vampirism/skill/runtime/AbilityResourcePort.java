package com.epicseed.vampirism.skill.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public interface AbilityResourcePort {

    boolean canAffordBlood(Ref<EntityStore> casterRef, int bloodCost);

    void spendBlood(Ref<EntityStore> casterRef, int bloodCost);
}
