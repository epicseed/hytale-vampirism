package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.systems.NightMarkedVictimSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntService {
    private NightHuntService() {
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        NightMarkedVictimSystem.clearPlayer(uuid);
    }

    public static void onPlayerConnect(@Nullable UUID uuid) {
        NightMarkedVictimSystem.onPlayerConnect(uuid);
    }

    public static void captureDisconnectState(@Nullable UUID uuid) {
        NightMarkedVictimSystem.captureDisconnectState(uuid);
    }

    public static boolean resetCooldown(@Nullable UUID uuid) {
        return NightMarkedVictimSystem.resetCooldown(uuid);
    }

    public static boolean forceStart(@Nullable UUID uuid,
                                     @Nullable Ref<EntityStore> playerRef,
                                     @Nonnull Store<EntityStore> store) {
        return NightMarkedVictimSystem.forceStart(uuid, playerRef, store);
    }

    public static NightMarkedVictimSystem.HuntDebugInfo getDebugInfo(@Nullable UUID uuid) {
        return NightMarkedVictimSystem.getDebugInfo(uuid);
    }

    public static int getBaseVisualTierForAcquiredPoints(int acquiredPoints) {
        return NightMarkedVictimSystem.getBaseVisualTierForAcquiredPoints(acquiredPoints);
    }

    public static void onPlayerKilledMarkedPrey(@Nullable UUID attackerUuid,
                                                @Nullable Ref<EntityStore> attackerRef,
                                                @Nullable Ref<EntityStore> preyRef,
                                                @Nonnull Store<EntityStore> store) {
        NightMarkedVictimSystem.onPlayerKilledMarkedPrey(attackerUuid, attackerRef, preyRef, store);
    }

    public static void recordMarkedPreyHit(@Nullable UUID attackerUuid,
                                           @Nullable Ref<EntityStore> preyRef,
                                           @Nonnull Store<EntityStore> store) {
        NightMarkedVictimSystem.recordMarkedPreyHit(attackerUuid, preyRef, store);
    }
}
