package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.skill.runtime.PlayerRegistrySkillProgressionAccess;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntFailureService {
    private static final String FAIL_PHASE_SUMMONING = "summoning";

    private NightHuntFailureService() {
    }

    public static void resolveFailState(@Nonnull UUID ownerUuid,
                                        @Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull TransformComponent playerTransform,
                                        @Nonnull String failurePhase,
                                        @Nonnull HuntState state,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                        int currentHour,
                                        float idleDelaySeconds,
                                        @Nonnull Runnable clearApproachMarker) {
        NightHuntSpawnRegistry.FailStateOption failState = NightHuntSpawnRegistry.get().pickFailState(
                new NightHuntSpawnRegistry.FailStateContext(
                        PlayerRegistrySkillProgressionAccess.instance().getAcquiredSkillPoints(ownerUuid),
                        state.completedWaypoints,
                        state.forced,
                        currentHour,
                        state.visualTier,
                        failurePhase));
        if (failState == null) {
            if (FAIL_PHASE_SUMMONING.equals(failurePhase)) {
                state.phase = HuntPhase.GUIDING;
                state.summonRemainingSeconds = 0f;
                NightHuntMessages.send(playerRef, store, NightHuntMessages.FAIL, "yellow");
                return;
            }
            resetToIdle(state, VampirismConfig.get().getNightHuntFailedCooldownSeconds(), idleDelaySeconds, clearApproachMarker);
            return;
        }

        if (failState.text() != null) {
            NightHuntMessages.send(playerRef, store, failState.text(), failState.textColor());
        }

        if (failState.resumeGuiding()) {
            state.phase = HuntPhase.GUIDING;
            state.summonRemainingSeconds = 0f;
            state.visualTier = NightHuntVisualService.clampVisualTier(Math.max(state.visualTier, failState.ambushVisualTier()));
            NightHuntCleanupService.clearGuideWisps(state, commandBuffer);
            return;
        }

        if (failState.ambushRoleId() != null) {
            NightHuntCleanupService.clearGuideWisps(state, commandBuffer);
            NightHuntCleanupService.clearWaypointDisplay(state, commandBuffer);
            NightHuntCleanupService.clearHelperRefs(state, commandBuffer);
            if (state.preyRef != null && state.preyRef.isValid()) {
                commandBuffer.tryRemoveEntity(state.preyRef, RemoveReason.REMOVE);
            }
            NightHuntTrackingService.clearPrey(state.preyEntityUuid);
            commandBuffer.run(bufferStore -> {
                if (!NightHuntPreySpawnService.spawnAmbushPrey(
                        ownerUuid,
                        playerRef,
                        playerTransform,
                        state,
                        failState,
                        bufferStore,
                        resolveWorld(bufferStore))) {
                    resetToIdle(state, failState.cooldownSeconds() > 0f
                            ? failState.cooldownSeconds()
                            : VampirismConfig.get().getNightHuntFailedCooldownSeconds(), idleDelaySeconds, clearApproachMarker);
                }
            });
            return;
        }

        resetToIdle(state, failState.cooldownSeconds() > 0f
                ? failState.cooldownSeconds()
                : VampirismConfig.get().getNightHuntFailedCooldownSeconds(), idleDelaySeconds, clearApproachMarker);
    }

    private static void resetToIdle(@Nonnull HuntState state,
                                    float cooldownSeconds,
                                    float idleDelaySeconds,
                                    @Nonnull Runnable clearApproachMarker) {
        NightHuntCleanupService.resetToIdle(state, cooldownSeconds, idleDelaySeconds, clearApproachMarker);
    }

    private static World resolveWorld(@Nonnull Store<EntityStore> store) {
        return WorldStoreAdapter.resolveWorld(store);
    }
}
