package com.epicseed.vampirism.runtime;
import com.epicseed.vampirism.modifier.ModifierContext;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.relic.RelicPresetProjectionService;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.TemporaryModifierTracker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PlayerRuntimeCleanupService {

    private PlayerRuntimeCleanupService() {
    }

    public static void cleanupDisconnectedPlayer(UUID uuid, @Nullable Ref<EntityStore> playerRef) {
        cleanupProjectedRelicInventory(uuid, playerRef);
        MorphFlySystem.captureDisconnectState(uuid);
        VampireVitalitySystem.captureDisconnectState(uuid);
        VampireVitalitySystem.clearPlayer(uuid);
        NightHuntService.captureDisconnectState(uuid);
        SkillTreeManager.get().evictPlayer(uuid);
        ModifierContext.REGISTRY.evict(uuid);
        EffectModifierSystem.clearPlayer(uuid);
        ProgressionLifecycleService.captureDisconnectProgress(uuid);
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
    }

    private static void cleanupProjectedRelicInventory(@Nonnull UUID uuid,
                                                       @Nullable Ref<EntityStore> playerRef) {
        if (playerRef == null) {
            RelicPresetProjectionService.clearPlayer(uuid);
            return;
        }
        Store<EntityStore> store = playerRef.getStore();
        if (store == null) {
            RelicPresetProjectionService.clearPlayer(uuid);
            return;
        }
        World world = WorldStoreAdapter.resolveWorld(store);
        if (world == null) {
            RelicPresetProjectionService.clearPlayer(uuid);
            return;
        }
        Runnable action = () -> RelicPresetProjectionService.sync(uuid, playerRef, store, false);
        if (world.isInThread()) {
            action.run();
            return;
        }
        world.execute(action);
    }
}
