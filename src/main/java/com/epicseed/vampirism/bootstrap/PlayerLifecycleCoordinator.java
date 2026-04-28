package com.epicseed.vampirism.bootstrap;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.hytale.WorldMapTrackerAdapter;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.TemporaryModifierTracker;
import com.epicseed.vampirism.systems.BloodConversionSystem;
import com.epicseed.vampirism.systems.BloodFeedSystem;
import com.epicseed.vampirism.systems.CrimsonUmbrellaVisualSystem;
import com.epicseed.vampirism.systems.EffectModifierSystem;
import com.epicseed.vampirism.systems.FormHealthSystem;
import com.epicseed.vampirism.systems.MorphFlySystem;
import com.epicseed.vampirism.systems.PassiveEffectSystem;
import com.epicseed.vampirism.systems.SneakSystem;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.systems.VampireCombatSystem;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.epicseed.vampirism.systems.VampireMovementSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
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
            MorphFlySystem.captureDisconnectState(uuid);
            VampireVitalitySystem.captureDisconnectState(uuid);
            PlayerSkillRegistry.get().setPersistedAbilityCooldowns(uuid, AbilityCooldownTracker.snapshotRemaining(uuid));
            NightHuntService.captureDisconnectState(uuid);
            SkillTreeManager.get().evictPlayer(uuid);
            ModifierRegistry.get().evict(uuid);
            EffectModifierSystem.clearPlayer(uuid);
            PlayerSkillRegistry.get().onPlayerDisconnect(uuid);
            MorphFlySystem.clearTransientState(uuid);
            FormHealthSystem.clearPlayer(uuid);
            BloodFeedSystem.clearPlayer(uuid);
            BloodConversionSystem.clearPlayer(uuid);
            NightHuntService.clearPlayer(uuid);
            SunburnSystem.onPlayerLeave(uuid);
            SneakSystem.clearPlayer(uuid);
            VampireMovementSystem.clearPlayer(uuid);
            VampireCombatSystem.clearPlayer(uuid);
            CrimsonUmbrellaVisualSystem.clearPlayer(uuid);
            VampireInfectionSystem.clearPlayer(uuid);
            AbilityCooldownTracker.clearPlayer(uuid);
            TemporaryModifierTracker.clearPlayer(uuid);
            PassiveEffectSystem.onPlayerDisconnect(uuid);
        });
    }
}
