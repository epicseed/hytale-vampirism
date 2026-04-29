package com.epicseed.vampirism.bootstrap;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.hytale.WorldMapTrackerAdapter;
import com.epicseed.vampirism.runtime.PlayerRuntimeCleanupService;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.systems.EffectModifierSystem;
import com.epicseed.vampirism.systems.MorphFlySystem;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;

public final class PlayerLifecycleCoordinator {

    private PlayerLifecycleCoordinator() {
    }

    public static void register(@Nonnull Vampirism plugin) {
        plugin.getEventRegistry().register(PlayerConnectEvent.class, e -> {
            UUID uuid = e.getPlayerRef().getUuid();
            PlayerSkillRegistry.get().onPlayerConnect(uuid);
            NightHuntService.onPlayerConnect(uuid);
            AbilityCooldownTracker.restorePlayer(uuid, PlayerSkillRegistry.get().getPersistedAbilityCooldowns(uuid));
            EffectModifierSystem.clearPlayer(uuid);
            SkillTreeManager.get().reloadModifiers(uuid);
            // Passive connect-time effects are applied lazily by PassiveEffectSystem once the
            // player's entity context is fully available (typically within the first tick).
        });
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, e -> {
            MorphFlySystem.onPlayerReady(e.getPlayerRef());
        });
        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, e -> {
            WorldMapTrackerAdapter.syncTransform(e.getHolder(), e.getWorld(), false);
        });
        plugin.getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, e -> {
            WorldMapTrackerAdapter.syncTransform(e.getHolder(), e.getWorld(), true);
        });
        plugin.getEventRegistry().register(PlayerDisconnectEvent.class, e -> {
            UUID uuid = e.getPlayerRef().getUuid();
            PlayerRuntimeCleanupService.cleanupDisconnectedPlayer(uuid);
        });
    }
}
