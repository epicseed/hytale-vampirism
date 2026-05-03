package com.epicseed.vampirism.runtime;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.hytale.runtime.PlayerRuntimeCleanupCoordinator;
import com.epicseed.epiccore.skill.runtime.passive.PassiveTriggerRuntimeService;
import com.epicseed.epiccore.skill.runtime.passive.PersistentPassiveEffectService;
import com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.relic.RelicPresetProjectionService;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
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

    @Nonnull
    public static PlayerRuntimeCleanupCoordinator create(
            @Nonnull PlayerSkillRegistry playerSkillRegistry,
            @Nonnull NightHuntService nightHuntService,
            @Nonnull TemporaryModifierTracker<StatType> temporaryModifiers,
            @Nonnull PassiveTriggerRuntimeService<SkillRuntimeContext> passiveTriggerRuntimeService,
            @Nonnull PersistentPassiveEffectService<SkillRuntimeContext> persistentPassiveEffectService) {
        return PlayerRuntimeCleanupCoordinator.of(
                PlayerRuntimeCleanupService::cleanupProjectedRelicInventory,
                (uuid, playerRef) -> MorphFlySystem.captureDisconnectState(uuid),
                (uuid, playerRef) -> VampireVitalitySystem.captureDisconnectState(uuid),
                (uuid, playerRef) -> VampireVitalitySystem.clearPlayer(uuid),
                (uuid, playerRef) -> nightHuntService.captureDisconnectState(uuid),
                (uuid, playerRef) -> SkillTreeManager.get().evictPlayer(uuid),
                (uuid, playerRef) -> ModifierContext.REGISTRY.evict(uuid),
                (uuid, playerRef) -> EffectModifierSystem.clearPlayer(uuid),
                (uuid, playerRef) -> playerSkillRegistry.captureDisconnectState(uuid),
                (uuid, playerRef) -> MorphFlySystem.clearTransientState(uuid),
                (uuid, playerRef) -> FormHealthSystem.clearPlayer(uuid),
                (uuid, playerRef) -> BloodFeedSystem.clearPlayer(uuid),
                (uuid, playerRef) -> BloodConversionSystem.clearPlayer(uuid),
                (uuid, playerRef) -> nightHuntService.clearPlayer(uuid),
                (uuid, playerRef) -> SunburnSystem.onPlayerLeave(uuid),
                (uuid, playerRef) -> SneakSystem.clearPlayer(uuid),
                (uuid, playerRef) -> VampireMovementSystem.clearPlayer(uuid),
                (uuid, playerRef) -> VampireCombatSystem.clearPlayer(uuid),
                (uuid, playerRef) -> CrimsonUmbrellaVisualSystem.clearPlayer(uuid),
                (uuid, playerRef) -> VampireInfectionSystem.clearPlayer(uuid),
                (uuid, playerRef) -> AbilityCooldownTracker.clearPlayer(uuid),
                (uuid, playerRef) -> temporaryModifiers.clearPlayer(uuid),
                (uuid, playerRef) -> PassiveEffectSystem.onPlayerDisconnect(
                        uuid,
                        passiveTriggerRuntimeService,
                        persistentPassiveEffectService));
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
