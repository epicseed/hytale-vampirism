package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntEventService {
    private NightHuntEventService() {
    }

    public static void applyRouteEvent(@Nonnull UUID ownerUuid,
                                       @Nonnull Ref<EntityStore> playerRef,
                                       @Nonnull TransformComponent playerTransform,
                                       @Nonnull HuntState state,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull CommandBuffer<EntityStore> commandBuffer,
                                       int currentHour) {
        NightHuntSpawnRegistry.RouteEventOption routeEvent = NightHuntSpawnRegistry.get().pickRouteEvent(
                new NightHuntSpawnRegistry.RouteEventContext(
                        PlayerSkillRegistry.get().getAcquiredSkillPoints(ownerUuid),
                        state.completedWaypoints,
                        state.forced,
                        currentHour,
                        state.visualTier));
        if (routeEvent == null) {
            return;
        }

        if (routeEvent.text() != null) {
            NightHuntMessages.send(playerRef, store, routeEvent.text(), routeEvent.textColor());
        }
        state.bonusWaypoints = Math.max(0, state.bonusWaypoints + routeEvent.extraWaypoints());
        state.visualTier = NightHuntVisualService.clampVisualTier(state.visualTier + routeEvent.visualTierDelta());
        for (int i = 0; i < routeEvent.instantGuideBursts(); i++) {
            final int burstIndex = i;
            commandBuffer.run(bufferStore -> NightHuntVisualService.spawnGuidePulse(
                    ownerUuid,
                    playerTransform,
                    state,
                    burstIndex,
                    Math.max(1, routeEvent.instantGuideBursts()),
                    bufferStore));
        }
    }
}
