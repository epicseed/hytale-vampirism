package com.epicseed.vampirism.domain.ritual;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.runtime.PlayerRuntimeIndex;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.registry.VampireStatusRegistry;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOutcomeTracker;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RuntimeVampiricRitualRewardPort extends ProgressionBackedVampiricRitualRewardPort {

    private final VampiricLineageService lineageService;

    public RuntimeVampiricRitualRewardPort(@Nonnull VampirismSkillProgressionAccess progressionAccess,
                                           @Nonnull VampiricLineageService lineageService) {
        super(progressionAccess);
        this.lineageService = lineageService;
    }

    @Override
    public void adjustBlood(UUID uuid, int delta) {
        if (uuid == null || delta == 0) {
            return;
        }
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        if (playerRef == null) {
            super.adjustBlood(uuid, delta);
            return;
        }
        if (delta > 0) {
            VampireVitalitySystem.addBlood(playerRef, delta);
        } else {
            VampireVitalitySystem.spendBlood(playerRef, -delta);
        }
        if (VampirePlayerStateStore.isInitialized()) {
            VampirePlayerStateStore.get().setPersistedBlood(uuid, VampireVitalitySystem.getBlood(playerRef));
        }
    }

    @Override
    public void setLineage(UUID uuid, String lineageId) {
        super.setLineage(uuid, lineageId);
        if (uuid != null) {
            lineageService.syncModifiers(uuid);
        }
    }

    @Override
    public void applySideEffect(UUID uuid, String ritualId, String sideEffectId) {
        if (uuid == null || sideEffectId == null || sideEffectId.isBlank()) {
            return;
        }
        switch (sideEffectId.trim()) {
            case VampiricRitualRegistry.SIDE_EFFECT_GRANT_PERMANENT_VAMPIRISM ->
                    VampireStatusRegistry.get().addVampire(uuid, resolvePlayerName(uuid));
            case VampiricRitualRegistry.SIDE_EFFECT_CLEAR_INFECTION -> {
                if (VampirePlayerStateStore.isInitialized()) {
                    VampirePlayerStateStore.get().clearInfection(uuid);
                }
            }
            default -> {
            }
        }
    }

    @Override
    public void onInstability(UUID uuid,
                              String ritualId,
                              int bloodLoss,
                              VampiricRitualRuntimePhase phase,
                              int interferenceCount) {
        super.onInstability(uuid, ritualId, bloodLoss, phase, interferenceCount);
        VampiricRitualOutcomeTracker.recordBacklash(uuid, ritualId, bloodLoss, phase, interferenceCount);
        sendRuntimeFeedback(
                uuid,
                "Ritual backlash: the coffin tears " + Math.max(0, bloodLoss)
                        + " blood from you"
                        + (interferenceCount > 0 ? " amid " + interferenceCount + " interference marks." : "."),
                "yellow");
    }

    @Override
    public void onCollapse(UUID uuid,
                           String ritualId,
                           int bloodLoss,
                           int interferenceCount,
                           double corruption) {
        super.onCollapse(uuid, ritualId, bloodLoss, interferenceCount, corruption);
        VampiricRitualOutcomeTracker.recordCollapse(uuid, ritualId, bloodLoss, interferenceCount, corruption);
        sendRuntimeFeedback(
                uuid,
                "Ritual collapse: the circle caves in, stripping " + Math.max(0, bloodLoss)
                        + " blood and leaving " + Math.round(Math.max(0d, corruption)) + "% corruption.",
                "red");
    }

    @Nonnull
    private static String resolvePlayerName(@Nonnull UUID uuid) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        if (playerRef == null) {
            return uuid.toString();
        }
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) {
            return uuid.toString();
        }
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        return playerRefComponent != null ? playerRefComponent.getUsername() : uuid.toString();
    }

    private static void sendRuntimeFeedback(@Nonnull UUID uuid, @Nonnull String message, @Nonnull String color) {
        Ref<EntityStore> playerRef = PlayerRuntimeIndex.get(uuid);
        if (playerRef == null) {
            return;
        }
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) {
            return;
        }
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent != null) {
            playerRefComponent.sendMessage(Message.raw(message).color(color));
        }
    }
}
