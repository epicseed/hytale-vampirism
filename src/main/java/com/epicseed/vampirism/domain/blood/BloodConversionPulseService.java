package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BloodConversionPulseService {
    private static final float FLOAT_EPSILON = 0.0001f;

    private BloodConversionPulseService() {
    }

    public static boolean canConvert(@Nonnull Ref<EntityStore> playerRef,
                                     @Nonnull Store<EntityStore> store,
                                     float minimumHealth) {
        EntityStatValue health = resolveHealth(playerRef, store);
        return health != null
                && health.getMax() > 0f
                && health.get() > minimumHealth + FLOAT_EPSILON;
    }

    public static boolean applyPulse(@Nonnull Ref<EntityStore> playerRef,
                                     @Nonnull BloodConversionSession session,
                                     @Nonnull Store<EntityStore> store) {
        EntityStatMap stats = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
        if (stats == null) {
            return false;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return false;
        }

        float maxDrain = health.get() - session.minimumHealth;
        if (maxDrain <= FLOAT_EPSILON) {
            return false;
        }

        int currentBlood = VampireVitalitySystem.getBlood(playerRef);
        int maxBlood = VampireVitalitySystem.getMaxBlood(playerRef);
        int missingBlood = Math.max(0, maxBlood - currentBlood);
        if (missingBlood <= 0) {
            return false;
        }

        float ratio = session.bloodGainPerTick / Math.max(FLOAT_EPSILON, session.healthCostPerTick);
        float drain = Math.min(session.healthCostPerTick, maxDrain);
        drain = Math.min(drain, missingBlood / Math.max(FLOAT_EPSILON, ratio));
        if (drain <= FLOAT_EPSILON) {
            return false;
        }

        int bloodGain = Math.min(missingBlood, Math.max(1, Math.round(drain * ratio)));
        if (bloodGain <= 0) {
            return false;
        }

        stats.addStatValue(DefaultEntityStatTypes.getHealth(), -drain);
        VampireVitalitySystem.addBlood(playerRef, bloodGain);
        return true;
    }

    private static EntityStatValue resolveHealth(@Nonnull Ref<EntityStore> playerRef,
                                                 @Nonnull Store<EntityStore> store) {
        EntityStatMap stats = (EntityStatMap) store.getComponent(playerRef, EntityStatMap.getComponentType());
        return stats != null ? stats.get(DefaultEntityStatTypes.getHealth()) : null;
    }
}
