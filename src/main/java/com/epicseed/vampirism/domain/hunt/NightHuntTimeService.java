package com.epicseed.vampirism.domain.hunt;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.config.VampirismConfig;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntTimeService {
    private NightHuntTimeService() {
    }

    public static int currentHour(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        return worldTime != null ? worldTime.getCurrentHour() : -1;
    }

    public static boolean isNightPeriod(@Nonnull Store<EntityStore> store) {
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        if (worldTime == null) {
            return false;
        }
        VampirismConfig config = VampirismConfig.get();
        return worldTime.getSunlightFactor() < 0.01d
                && !worldTime.isDayTimeWithinRange(config.getDayStartHour(), config.getNightStartHour());
    }
}
