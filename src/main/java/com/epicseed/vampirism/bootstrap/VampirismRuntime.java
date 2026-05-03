package com.epicseed.vampirism.bootstrap;

import java.nio.file.Path;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.WorldMapTrackerAdapter;
import com.epicseed.epiccore.hytale.runtime.PlayerRuntimeCleanupCoordinator;
import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.runtime.CatalogBackedSkillRuntimeBootstrap;
import com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker;
import com.epicseed.epiccore.skill.runtime.passive.PassiveRuntimeServices;
import com.epicseed.epiccore.skill.runtime.passive.PassiveTriggerRuntimeService;
import com.epicseed.epiccore.skill.runtime.passive.PersistentPassiveEffectService;
import com.epicseed.epiccore.skill.runtime.passive.TriggerDispatcher;
import com.epicseed.epiccore.skill.runtime.passive.TriggerEvent;
import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.commands.VampirismCommand;
import com.epicseed.vampirism.commands.VampirismPotionCommand;
import com.epicseed.vampirism.commands.VampirismRelicBindingCommand;
import com.epicseed.vampirism.commands.VampirismRelicBindingsCommand;
import com.epicseed.vampirism.commands.VampirismRelicCommand;
import com.epicseed.vampirism.commands.VampirismSkillTreeCommand;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.blood.BloodHudService;
import com.epicseed.vampirism.domain.blood.FeedCompletionService;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
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
import com.epicseed.vampirism.skill.runtime.ModifierScopeMatcher;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.SkillActionExecutor;
import com.epicseed.vampirism.skill.runtime.SkillConditionEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillRequirementEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeStateResolver;
import com.epicseed.vampirism.skill.runtime.VampireVitalityAbilityResourcePort;
import com.epicseed.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.systems.BloodConversionSystem;
import com.epicseed.vampirism.systems.BloodFeedSystem;
import com.epicseed.vampirism.systems.CrimsonUmbrellaVisualSystem;
import com.epicseed.vampirism.systems.EffectModifierSystem;
import com.epicseed.vampirism.systems.FormHealthSystem;
import com.epicseed.vampirism.systems.MorphFlySystem;
import com.epicseed.vampirism.systems.NightMarkedVictimSystem;
import com.epicseed.vampirism.systems.PassiveEffectSystem;
import com.epicseed.vampirism.systems.RelicChestLockSystem;
import com.epicseed.vampirism.systems.RelicDeathDropPreventSystem;
import com.epicseed.vampirism.systems.RelicDropPreventSystem;
import com.epicseed.vampirism.systems.SneakSystem;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.systems.VampireCombatSystem;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.epicseed.vampirism.systems.VampireMovementSystem;
import com.epicseed.vampirism.systems.VampireSleepSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.epicseed.vampirism.ui.VampirismProgressionPageFactory;
import com.epicseed.vampirism.ui.VampirismRelicUiAdapter;
import com.epicseed.vampirism.ui.VampirismSkillTreeUiAdapter;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismRuntime {

    private final PlayerSkillRegistry playerSkillRegistry;
    private final PlayerRuntimeCleanupCoordinator playerRuntimeCleanupCoordinator;
    private final NightHuntService nightHuntService;

    private VampirismRuntime(@Nonnull PlayerSkillRegistry playerSkillRegistry,
                             @Nonnull PlayerRuntimeCleanupCoordinator playerRuntimeCleanupCoordinator,
                             @Nonnull NightHuntService nightHuntService) {
        this.playerSkillRegistry = playerSkillRegistry;
        this.playerRuntimeCleanupCoordinator = playerRuntimeCleanupCoordinator;
        this.nightHuntService = nightHuntService;
    }

    @Nonnull
    public static VampirismRuntime bootstrap(@Nonnull Vampirism plugin,
                                             @Nonnull CatalogBackedSkillRuntimeBootstrap skillRuntimeBootstrap,
                                             @Nonnull Vector2d highestPosition) {
        Path dataDirectory = plugin.getDataDirectory();
        VampireStatusRegistry.init(dataDirectory);
        PlayerSkillRegistry playerSkillRegistry = PlayerSkillRegistry.init(dataDirectory);
        VampirismClassifications.registerProvider();

        VampirismSkillProgressionAccess progressionAccess = playerSkillRegistry.progressionAccess();
        ProgressionDefinitionProvider progressionDefinitionProvider =
                skillRuntimeBootstrap.progressionDefinitions();
        NightHuntService nightHuntService = new NightHuntService(progressionAccess);
        SkillRuntimeStateResolver skillRuntimeStateResolver =
                new SkillRuntimeStateResolver(skillRuntimeBootstrap.runtimeBindings()::snapshot);
        SkillConditionEvaluator skillConditionEvaluator =
                new SkillConditionEvaluator(progressionDefinitionProvider, skillRuntimeStateResolver);
        SkillRequirementEvaluator skillRequirementEvaluator = new SkillRequirementEvaluator(
                progressionDefinitionProvider,
                skillConditionEvaluator,
                () -> progressionAccess);
        ModifierScopeMatcher modifierScopeMatcher = new ModifierScopeMatcher(skillConditionEvaluator);
        TemporaryModifierTracker<StatType> temporaryModifiers = new TemporaryModifierTracker<>();
        SkillActionExecutor skillActionExecutor = new SkillActionExecutor(
                progressionDefinitionProvider,
                skillConditionEvaluator,
                skillRequirementEvaluator,
                temporaryModifiers);

        RelicBindingService.init(
                skillRuntimeBootstrap.runtimeBindings()::snapshot,
                progressionDefinitionProvider,
                progressionAccess,
                CooldownTrackerAbilityCooldownAccess.instance());

        SkillTreeManager skillTreeManager = new SkillTreeManager(
                progressionDefinitionProvider,
                progressionAccess,
                modifierScopeMatcher);
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

        PassiveRuntimeServices<SkillRuntimeContext, PassiveService> passiveRuntimeServices =
                PassiveRuntimeServices.create(
                        progressionDefinitionProvider,
                        progressionAccess,
                        skillActionExecutor.executor(),
                        skillConditionEvaluator::evaluateAll,
                        PassiveService::new);
        TriggerDispatcher<SkillRuntimeContext> triggerDispatcher = passiveRuntimeServices.triggerDispatcher();
        PassiveService passiveService = passiveRuntimeServices.passiveService();
        PassiveTriggerRuntimeService<SkillRuntimeContext> passiveTriggerRuntimeService =
                passiveRuntimeServices.passiveTriggerRuntimeService();
        PersistentPassiveEffectService<SkillRuntimeContext> persistentPassiveEffectService =
                passiveRuntimeServices.persistentPassiveEffectService();

        skillActionExecutor.setOnFinalBlow(passiveService::onFeed);
        FeedCompletionService.init(
                passiveService::onFeed,
                nightHuntService::onPlayerKilledMarkedPrey);
        BloodHudService.init(playerRef ->
                new RelicCooldownHud(playerRef, VampirismUiPaths.theme(), relicUiAdapter));
        AbilityService abilityService = new AbilityService(skillRequirementEvaluator, skillActionExecutor);
        abilityService.init(
                progressionDefinitionProvider,
                progressionAccess,
                VampireInfectionSystem::allowsTemporaryAbility,
                new VampireVitalityAbilityResourcePort(),
                (ctx, abilityId) -> triggerDispatcher.dispatch(TriggerEvent.onActivate(ctx, abilityId)));
        skillActionExecutor.setAbilityActivator(abilityService::activateFromAction);

        NightHuntSpawnRegistry.init();
        SunburnSystem.registerModifiers();
        VampireVitalitySystem.registerModifiers();
        VampireMovementSystem.registerModifiers(temporaryModifiers);
        VampireCombatSystem.registerModifiers();
        RelicPresetSelectionAdapter.init();

        VampirismRuntime runtime = new VampirismRuntime(
                playerSkillRegistry,
                PlayerRuntimeCleanupService.create(
                        playerSkillRegistry,
                        nightHuntService,
                        temporaryModifiers,
                        passiveTriggerRuntimeService,
                        persistentPassiveEffectService),
                nightHuntService);
        runtime.registerPlayerLifecycle(plugin);
        registerSystems(
                plugin,
                passiveService,
                passiveTriggerRuntimeService,
                persistentPassiveEffectService,
                progressionDefinitionProvider,
                progressionAccess,
                modifierScopeMatcher,
                nightHuntService,
                skillRuntimeStateResolver);
        registerCommands(plugin, progressionAccess, nightHuntService, abilityService, progressionPageFactory);
        return runtime;
    }

    public void shutdown() {
        VampirismClassifications.unregisterProvider();
        RelicPresetSelectionAdapter.shutdown();
    }

    private void registerPlayerLifecycle(@Nonnull Vampirism plugin) {
        plugin.getEventRegistry().register(PlayerConnectEvent.class, e -> onPlayerConnect(e.getPlayerRef().getUuid()));
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, e -> MorphFlySystem.onPlayerReady(e.getPlayerRef()));
        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, e ->
                WorldMapTrackerAdapter.syncTransform(e.getHolder(), e.getWorld(), false));
        plugin.getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, e ->
                WorldMapTrackerAdapter.syncTransform(e.getHolder(), e.getWorld(), true));
        plugin.getEventRegistry().register(PlayerDisconnectEvent.class, e ->
                onPlayerDisconnect(e.getPlayerRef().getUuid(), e.getPlayerRef().getReference()));
    }

    private void onPlayerConnect(@Nonnull UUID uuid) {
        playerSkillRegistry.restoreRuntimeState(uuid);
        nightHuntService.onPlayerConnect(uuid);
        EffectModifierSystem.clearPlayer(uuid);
        SkillTreeManager.get().reloadModifiers(uuid);
        // Passive connect-time effects are applied lazily by PassiveEffectSystem once the
        // player's entity context is fully available (typically within the first tick).
    }

    private void onPlayerDisconnect(@Nonnull UUID uuid, @Nullable Ref<EntityStore> playerRef) {
        playerRuntimeCleanupCoordinator.cleanup(uuid, playerRef);
    }

    private static void registerCommands(@Nonnull Vampirism plugin,
                                         @Nonnull VampirismSkillProgressionAccess progressionAccess,
                                         @Nonnull NightHuntService nightHuntService,
                                         @Nonnull AbilityService abilityService,
                                         @Nonnull ProgressionPageFactory progressionPageFactory) {
        plugin.getCommandRegistry().registerCommand(
                new VampirismCommand(progressionAccess, nightHuntService, abilityService));
        plugin.getCommandRegistry().registerCommand(new VampirismSkillTreeCommand(progressionPageFactory));
        plugin.getCommandRegistry().registerCommand(new VampirismPotionCommand());
        plugin.getCommandRegistry().registerCommand(new VampirismRelicCommand(abilityService));
        plugin.getCommandRegistry().registerCommand(new VampirismRelicBindingsCommand(progressionPageFactory));
        plugin.getCommandRegistry().registerCommand(new VampirismRelicBindingCommand(progressionPageFactory));
    }

    private static void registerSystems(@Nonnull Vampirism plugin,
                                        @Nonnull PassiveService passiveService,
                                        @Nonnull PassiveTriggerRuntimeService<SkillRuntimeContext> passiveTriggerRuntimeService,
                                        @Nonnull PersistentPassiveEffectService<SkillRuntimeContext> persistentPassiveEffectService,
                                        @Nonnull ProgressionDefinitionProvider progressionDefinitionProvider,
                                        @Nonnull VampirismSkillProgressionAccess progressionAccess,
                                        @Nonnull ModifierScopeMatcher modifierScopeMatcher,
                                        @Nonnull NightHuntService nightHuntService,
                                        @Nonnull SkillRuntimeStateResolver skillRuntimeStateResolver) {
        plugin.getEntityStoreRegistry().registerSystem(new VampireInfectionSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireVitalitySystem());
        plugin.getEntityStoreRegistry().registerSystem(new BloodFeedSystem(progressionAccess));
        plugin.getEntityStoreRegistry().registerSystem(new BloodConversionSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireCombatSystem(
                passiveService,
                nightHuntService,
                skillRuntimeStateResolver));
        plugin.getEntityStoreRegistry().registerSystem(new VampireMovementSystem());
        plugin.getEntityStoreRegistry().registerSystem(new EffectModifierSystem(modifierScopeMatcher));
        plugin.getEntityStoreRegistry().registerSystem(new FormHealthSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SunburnSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SneakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new MorphFlySystem());
        plugin.getEntityStoreRegistry().registerSystem(new CrimsonUmbrellaVisualSystem());
        plugin.getEntityStoreRegistry().registerSystem(new NightMarkedVictimSystem(progressionAccess));
        plugin.getEntityStoreRegistry().registerSystem(new PassiveEffectSystem(
                passiveService,
                passiveTriggerRuntimeService,
                persistentPassiveEffectService,
                progressionDefinitionProvider,
                progressionAccess));
        plugin.getEntityStoreRegistry().registerSystem(new RelicDropPreventSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RelicDeathDropPreventSystem());
        plugin.getEntityStoreRegistry().registerSystem(new RelicChestLockSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireSleepSystem());
    }
}
