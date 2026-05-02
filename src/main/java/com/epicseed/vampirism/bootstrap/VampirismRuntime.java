package com.epicseed.vampirism.bootstrap;

import java.nio.file.Path;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.runtime.CatalogBackedSkillRuntimeBootstrap;
import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.blood.BloodHudService;
import com.epicseed.vampirism.domain.blood.FeedCompletionService;
import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.epicseed.vampirism.domain.skill.SkillTreePresenter;
import com.epicseed.vampirism.hud.RelicCooldownHud;
import com.epicseed.vampirism.hytale.RelicPresetSelectionAdapter;
import com.epicseed.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.runtime.PlayerRuntimeCleanupService;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityService;
import com.epicseed.vampirism.skill.runtime.CooldownTrackerAbilityCooldownAccess;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.PassiveTriggerRuntimeService;
import com.epicseed.vampirism.skill.runtime.SkillActionExecutor;
import com.epicseed.vampirism.skill.runtime.TriggerDispatcher;
import com.epicseed.vampirism.skill.runtime.TriggerEvent;
import com.epicseed.vampirism.skill.runtime.VampireVitalityAbilityResourcePort;
import com.epicseed.vampirism.skill.runtime.VampirismAbilityAccessProvider;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.systems.VampireCombatSystem;
import com.epicseed.vampirism.systems.VampireMovementSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.epicseed.vampirism.ui.VampirismProgressionPageFactory;
import com.epicseed.vampirism.ui.VampirismRelicUiAdapter;
import com.epicseed.vampirism.ui.VampirismSkillTreeUiAdapter;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismRuntime {

    private final PlayerSkillRegistry playerSkillRegistry;
    private final ProgressionDefinitionProvider progressionDefinitionProvider;
    private final SkillProgressionAccess progressionAccess;
    private final PassiveService passiveService;
    private final ProgressionPageFactory progressionPageFactory;

    private VampirismRuntime(@Nonnull PlayerSkillRegistry playerSkillRegistry,
                             @Nonnull ProgressionDefinitionProvider progressionDefinitionProvider,
                             @Nonnull SkillProgressionAccess progressionAccess,
                             @Nonnull PassiveService passiveService,
                             @Nonnull ProgressionPageFactory progressionPageFactory) {
        this.playerSkillRegistry = playerSkillRegistry;
        this.progressionDefinitionProvider = progressionDefinitionProvider;
        this.progressionAccess = progressionAccess;
        this.passiveService = passiveService;
        this.progressionPageFactory = progressionPageFactory;
    }

    @Nonnull
    public static VampirismRuntime create(@Nonnull Path dataDirectory,
                                          @Nonnull CatalogBackedSkillRuntimeBootstrap skillRuntimeBootstrap,
                                          @Nonnull Vector2d highestPosition) {
        VampireStatusRegistry.init(dataDirectory);
        PlayerSkillRegistry playerSkillRegistry = PlayerSkillRegistry.init(dataDirectory);
        VampirismClassifications.registerProvider();

        SkillProgressionAccess progressionAccess = playerSkillRegistry.progressionAccess();
        ProgressionDefinitionProvider progressionDefinitionProvider =
                skillRuntimeBootstrap.progressionDefinitions();

        RelicBindingService.init(
                skillRuntimeBootstrap.runtimeBindings()::snapshot,
                progressionDefinitionProvider,
                progressionAccess,
                CooldownTrackerAbilityCooldownAccess.instance());

        SkillTreeManager skillTreeManager = new SkillTreeManager(
                progressionDefinitionProvider,
                progressionAccess);
        SkillTreePresenter skillTreePresenter = new SkillTreePresenter(
                progressionDefinitionProvider,
                progressionAccess);
        VampirismSkillTreeUiAdapter skillTreeUiAdapter = new VampirismSkillTreeUiAdapter(
                progressionDefinitionProvider,
                progressionAccess,
                () -> highestPosition,
                skillTreePresenter,
                skillTreeManager);
        VampirismRelicUiAdapter relicUiAdapter = new VampirismRelicUiAdapter(
                progressionDefinitionProvider,
                () -> VampirismConfig.get().getCooldownHudUpdateIntervalMs());
        ProgressionPageFactory progressionPageFactory = new VampirismProgressionPageFactory(
                VampirismUiPaths.theme(),
                skillTreeUiAdapter,
                relicUiAdapter);

        TriggerDispatcher triggerDispatcher = new TriggerDispatcher(
                progressionDefinitionProvider,
                progressionAccess);
        PassiveService passiveService = new PassiveService(
                progressionDefinitionProvider,
                progressionAccess,
                triggerDispatcher::dispatch);

        PassiveTriggerRuntimeService.init(triggerDispatcher::dispatch);
        SkillActionExecutor.init(passiveService::onFeed);
        FeedCompletionService.init(passiveService::onFeed);
        BloodHudService.init(playerRef ->
                new RelicCooldownHud(playerRef, VampirismUiPaths.theme(), relicUiAdapter));
        AbilityService.init(
                progressionDefinitionProvider,
                new VampirismAbilityAccessProvider(playerSkillRegistry),
                new VampireVitalityAbilityResourcePort(),
                (ctx, abilityId) -> triggerDispatcher.dispatch(TriggerEvent.onActivate(ctx, abilityId)));

        NightHuntSpawnRegistry.init();
        SunburnSystem.registerModifiers();
        VampireVitalitySystem.registerModifiers();
        VampireMovementSystem.registerModifiers();
        VampireCombatSystem.registerModifiers();
        RelicPresetSelectionAdapter.init();

        return new VampirismRuntime(
                playerSkillRegistry,
                progressionDefinitionProvider,
                progressionAccess,
                passiveService,
                progressionPageFactory);
    }

    public void shutdown() {
        VampirismClassifications.unregisterProvider();
        RelicPresetSelectionAdapter.shutdown();
    }

    void restorePlayerProgression(@Nonnull UUID uuid) {
        playerSkillRegistry.restoreRuntimeState(uuid);
    }

    void cleanupDisconnectedPlayer(@Nonnull UUID uuid,
                                   @Nullable Ref<EntityStore> playerRef) {
        PlayerRuntimeCleanupService.cleanupDisconnectedPlayer(uuid, playerRef, playerSkillRegistry);
    }

    @Nonnull
    PassiveService passiveService() {
        return passiveService;
    }

    @Nonnull
    ProgressionDefinitionProvider progressionDefinitionProvider() {
        return progressionDefinitionProvider;
    }

    @Nonnull
    SkillProgressionAccess progressionAccess() {
        return progressionAccess;
    }

    @Nonnull
    ProgressionPageFactory progressionPageFactory() {
        return progressionPageFactory;
    }
}
