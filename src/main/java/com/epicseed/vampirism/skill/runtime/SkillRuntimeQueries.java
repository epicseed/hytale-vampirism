package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

final class SkillRuntimeQueries {

    private SkillRuntimeQueries() {}

    static boolean isNight(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        return worldTime != null && worldTime.getSunlightFactor() < 0.01d;
    }
}
