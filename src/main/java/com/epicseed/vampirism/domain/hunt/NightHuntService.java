package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.systems.NightMarkedVictimSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntService {
    private final VampirismSkillProgressionAccess progressionAccess;

    public NightHuntService(@Nonnull VampirismSkillProgressionAccess progressionAccess) {
        this.progressionAccess = progressionAccess;
    }

    public void clearPlayer(@Nullable UUID uuid) {
        NightMarkedVictimSystem.clearPlayer(uuid);
    }

    public void onPlayerConnect(@Nullable UUID uuid) {
        NightMarkedVictimSystem.onPlayerConnect(uuid);
    }

    public void captureDisconnectState(@Nullable UUID uuid) {
        NightMarkedVictimSystem.captureDisconnectState(uuid);
    }

    public boolean resetCooldown(@Nullable UUID uuid) {
        return NightMarkedVictimSystem.resetCooldown(uuid);
    }

    public boolean forceStart(@Nullable UUID uuid,
                              @Nullable Ref<EntityStore> playerRef,
                              @Nonnull Store<EntityStore> store) {
        return NightMarkedVictimSystem.forceStart(uuid, playerRef, store, progressionAccess);
    }

    public NightHuntDebugInfo getDebugInfo(@Nullable UUID uuid) {
        return NightMarkedVictimSystem.getDebugInfo(uuid);
    }

    public int getBaseVisualTierForAcquiredPoints(int acquiredPoints) {
        return NightMarkedVictimSystem.getBaseVisualTierForAcquiredPoints(acquiredPoints);
    }

    public void onPlayerKilledMarkedPrey(@Nullable UUID attackerUuid,
                                         @Nullable Ref<EntityStore> attackerRef,
                                         @Nullable Ref<EntityStore> preyRef,
                                         @Nonnull Store<EntityStore> store) {
        NightMarkedVictimSystem.onPlayerKilledMarkedPrey(attackerUuid, attackerRef, preyRef, store, progressionAccess);
    }

    public void recordMarkedPreyHit(@Nullable UUID attackerUuid,
                                    @Nullable Ref<EntityStore> preyRef,
                                    @Nonnull Store<EntityStore> store) {
        NightMarkedVictimSystem.recordMarkedPreyHit(attackerUuid, preyRef, store);
    }
}
