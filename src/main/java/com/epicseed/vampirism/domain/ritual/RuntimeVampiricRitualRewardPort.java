package com.epicseed.vampirism.domain.ritual;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.runtime.PlayerRuntimeIndex;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.registry.VampireStatusRegistry;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
}
