package com.epicseed.vampirism.bootstrap;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.WorldStoreAdapter;
import com.epicseed.epiccore.hytale.WorldMapTrackerAdapter;
import com.epicseed.epiccore.hytale.hud.HudBackendResolver;
import com.epicseed.epiccore.hytale.hud.SingleSlotHudCoordinator;
import com.epicseed.epiccore.hytale.runtime.PlayerRuntimeCleanupCoordinator;
import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.relic.application.RelicInventoryConfig;
import com.epicseed.epiccore.relic.application.RelicInventoryService;
import com.epicseed.epiccore.relic.application.RelicPresetProjectionConfig;
import com.epicseed.epiccore.relic.application.RelicPresetProjectionService;
import com.epicseed.epiccore.relic.domain.RelicBindingService;
import com.epicseed.epiccore.relic.infrastructure.RelicPresetSelectionAdapter;
import com.epicseed.epiccore.relic.presentation.StandardRelicUiAdapter;
import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.runtime.CatalogBackedSkillRuntimeBootstrap;
import com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker;
import com.epicseed.epiccore.skill.runtime.passive.PassiveRuntimeServices;
import com.epicseed.epiccore.skill.runtime.passive.PassiveTriggerRuntimeService;
import com.epicseed.epiccore.skill.runtime.passive.PersistentPassiveEffectService;
import com.epicseed.epiccore.skill.runtime.passive.TriggerDispatcher;
import com.epicseed.epiccore.skill.runtime.passive.TriggerEvent;
import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.skill.ui.ProgressionRelicCooldownHud;
import com.epicseed.epiccore.vampirism.runtime.VampiricPlayerRuntimeCleanupService;
import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.commands.VampirismCommand;
import com.epicseed.vampirism.commands.VampirismHuntCompendiumCommand;
import com.epicseed.vampirism.commands.VampirismPotionCommand;
import com.epicseed.vampirism.commands.VampirismRelicBindingCommand;
import com.epicseed.vampirism.commands.VampirismRelicBindingsCommand;
import com.epicseed.vampirism.commands.VampirismRelicCommand;
import com.epicseed.vampirism.commands.VampirismRitualCommand;
import com.epicseed.vampirism.commands.VampirismSkillTreeCommand;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.blood.BloodHudService;
import com.epicseed.vampirism.domain.blood.FeedCompletionService;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;
import com.epicseed.vampirism.domain.lineage.VampiricLineageRegistry;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatPolicy;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.relic.PlayerRelicBindingsStore;
import com.epicseed.vampirism.domain.relic.VampiricRelicSetService;
import com.epicseed.vampirism.domain.ritual.RuntimeVampiricRitualRewardPort;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplateRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualCompanionTracker;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualFeedbackService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualOfferingRecoveryService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualSelectionService;
import com.epicseed.vampirism.hud.BloodGaugeHud;
import com.epicseed.vampirism.hud.NightHuntHudService;
import com.epicseed.vampirism.hud.NightHuntStatusHud;
import com.epicseed.vampirism.hud.RitualHudService;
import com.epicseed.vampirism.hud.RitualStatusHud;
import com.epicseed.vampirism.hytale.interaction.VampirismInteractionRuntime;
import com.epicseed.vampirism.hytale.interaction.VampirismRitualToolActions;
import com.epicseed.vampirism.hytale.PlayerWorldLifecycleEventAdapter;
import com.epicseed.vampirism.hytale.VampirismPlayerFeedback;
import com.epicseed.vampirism.hytale.ritual.RitualOfferingSurfaceInteraction;
import com.epicseed.vampirism.domain.skill.SkillTreePresenter;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.epiccore.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.epiccore.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityService;
import com.epicseed.epiccore.vampirism.skill.runtime.CooldownTrackerAbilityCooldownAccess;
import com.epicseed.vampirism.skill.runtime.ModifierScopeMatcher;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.SkillActionExecutor;
import com.epicseed.vampirism.skill.runtime.SkillConditionEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillRequirementEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.epiccore.vampirism.skill.runtime.SkillRuntimeStateResolver;
import com.epicseed.vampirism.skill.runtime.VampireVitalityAbilityResourcePort;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.systems.BloodConversionSystem;
import com.epicseed.vampirism.systems.BloodFeedSystem;
import com.epicseed.vampirism.systems.CrimsonUmbrellaVisualSystem;
import com.epicseed.vampirism.systems.EffectModifierSystem;
import com.epicseed.vampirism.systems.FormHealthSystem;
import com.epicseed.vampirism.systems.MorphFlySystem;
import com.epicseed.vampirism.systems.NightHuntHudSystem;
import com.epicseed.vampirism.systems.NightMarkedVictimSystem;
import com.epicseed.vampirism.systems.PassiveEffectSystem;
import com.epicseed.vampirism.systems.RelicChestLockSystem;
import com.epicseed.vampirism.systems.RelicDeathDropPreventSystem;
import com.epicseed.vampirism.systems.RelicDropPreventSystem;
import com.epicseed.vampirism.systems.SneakSystem;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.systems.VampireCombatSystem;
import com.epicseed.vampirism.systems.VampiricSystemsSupport;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.epicseed.vampirism.systems.VampireMovementSystem;
import com.epicseed.vampirism.systems.VampireSleepSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.epicseed.vampirism.systems.VampiricRitualCompanionSystem;
import com.epicseed.vampirism.systems.VampiricRitualSystem;
import com.epicseed.vampirism.ui.VampirismProgressionPageFactory;
import com.epicseed.vampirism.ui.VampirismSettingsUiAdapter;
import com.epicseed.vampirism.ui.VampirismSkillTreeUiAdapter;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.epicseed.vampirism.ui.SkillTreeUI;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import org.joml.Vector2d;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismRuntime {

    private final PlayerSkillRegistry playerSkillRegistry;
    private final PlayerRuntimeCleanupCoordinator playerRuntimeCleanupCoordinator;
    private final NightHuntService nightHuntService;
    private final RelicPresetSelectionAdapter relicPresetSelectionAdapter;
    private final VampiricLineageService lineageService;
    private final Set<UUID> connectedPlayers = ConcurrentHashMap.newKeySet();

    private VampirismRuntime(@Nonnull PlayerSkillRegistry playerSkillRegistry,
                             @Nonnull PlayerRuntimeCleanupCoordinator playerRuntimeCleanupCoordinator,
                             @Nonnull NightHuntService nightHuntService,
                             @Nonnull RelicPresetSelectionAdapter relicPresetSelectionAdapter,
                             @Nonnull VampiricLineageService lineageService) {
        this.playerSkillRegistry = playerSkillRegistry;
        this.playerRuntimeCleanupCoordinator = playerRuntimeCleanupCoordinator;
        this.nightHuntService = nightHuntService;
        this.relicPresetSelectionAdapter = relicPresetSelectionAdapter;
        this.lineageService = lineageService;
    }

    @Nonnull
    public static VampirismRuntime bootstrap(@Nonnull Vampirism plugin,
                                             @Nonnull CatalogBackedSkillRuntimeBootstrap skillRuntimeBootstrap,
                                             @Nonnull java.util.function.Supplier<Vector2d> highestPositionSupplier) {
        Path dataDirectory = plugin.getPersistentDataDirectory();
        VampireStatusRegistry.init(dataDirectory, () -> VampirismConfig.get().isVampireDefaultEnabled());
        PlayerSkillRegistry playerSkillRegistry = PlayerSkillRegistry.init(dataDirectory);
        VampirismClassifications.registerProvider();

        VampirismSkillProgressionAccess progressionAccess = playerSkillRegistry.progressionAccess();
        ProgressionDefinitionProvider progressionDefinitionProvider =
                skillRuntimeBootstrap.progressionDefinitions();
        NightHuntService nightHuntService = new NightHuntService(progressionAccess);
        SkillRuntimeStateResolver<ModifierContext, SkillRuntimeContext> skillRuntimeStateResolver =
                new SkillRuntimeStateResolver<>(
                        skillRuntimeBootstrap.runtimeBindings()::snapshot,
                        new com.epicseed.epiccore.skill.runtime.HytaleRuntimeStateResolver.StateContextAccess<>() {
                            @Override
                            public Ref<EntityStore> selfRef(ModifierContext context) {
                                return context.ref();
                            }

                            @Override
                            public com.hypixel.hytale.component.Store<EntityStore> store(ModifierContext context) {
                                return context.store();
                            }

                            @Override
                            public boolean resolve(ModifierContext context,
                                                   com.epicseed.epiccore.modifier.ContextKey<Boolean> key,
                                                   java.util.function.Supplier<Boolean> supplier) {
                                return context.resolve(key, supplier);
                            }
                        },
                        new com.epicseed.epiccore.skill.runtime.HytaleRuntimeStateResolver.StateContextAccess<>() {
                            @Override
                            public Ref<EntityStore> selfRef(SkillRuntimeContext context) {
                                return context.ref();
                            }

                            @Override
                            public com.hypixel.hytale.component.Store<EntityStore> store(SkillRuntimeContext context) {
                                return context.store();
                            }
                        },
                        SkillRuntimeContext::modifierContext,
                        (stateId, context, stateQuery) -> {
                            if (stateId == null || stateId.isBlank() || context == null) {
                                return null;
                            }
                            return switch (stateId) {
                                case "IN_SUNLIGHT" -> context.uuid() != null
                                        && context.resolve(SunburnSystem.IN_SUNLIGHT,
                                        () -> SunburnSystem.isInSunlight(context.uuid()));
                                case "IS_STARVING" -> context.resolve(VampireVitalitySystem.IS_STARVING,
                                        () -> VampireVitalitySystem.isStarving(context.ref()));
                                case "IS_OVERFED" -> context.resolve(VampireVitalitySystem.IS_OVERFED,
                                        () -> VampireVitalitySystem.isOverfed(context.ref()));
                                case "IS_BLOOD_STATE_NORMAL" -> context.resolve(
                                        VampireVitalitySystem.IS_BLOOD_STATE_NORMAL,
                                        () -> VampireVitalitySystem.isBloodStateNormal(context.ref()));
                                case "IS_IN_BAT_FORM" -> context.uuid() != null
                                        && context.resolve(MorphFlySystem.IS_IN_BAT_FORM,
                                        () -> MorphFlySystem.isMorphActive(
                                                context.ref(),
                                                context.store(),
                                                context.uuid()));
                                case "IS_IN_FRENZY" -> stateQuery.apply("IS_IN_BLOOD_THIRST", context);
                                default -> null;
                            };
                        });
        SkillConditionEvaluator skillConditionEvaluator =
                new SkillConditionEvaluator(progressionDefinitionProvider, skillRuntimeStateResolver);
        ModifierScopeMatcher modifierScopeMatcher = new ModifierScopeMatcher(skillConditionEvaluator);
        TemporaryModifierTracker<StatType> temporaryModifiers = new TemporaryModifierTracker<>();
        MasqueradeHeatService masqueradeHeatService = new MasqueradeHeatService(MasqueradeHeatPolicy.defaults());
        VampiricLineageService lineageService = new VampiricLineageService(
                new VampiricLineageRegistry(),
                progressionAccess);
        VampiricRitualService ritualService = new VampiricRitualService(
                new VampiricRitualRegistry(),
                new RuntimeVampiricRitualRewardPort(
                        progressionAccess,
                        lineageService,
                        nightHuntService,
                        masqueradeHeatService,
                        temporaryModifiers));
        VampiricRitualTemplateRegistry ritualTemplateRegistry = new VampiricRitualTemplateRegistry();
        VampiricRitualRuntimeService ritualRuntimeService =
                new VampiricRitualRuntimeService(ritualService, ritualTemplateRegistry);
        RitualOfferingSurfaceInteraction.installRuntime(ritualRuntimeService);
        VampiricRitualContextResolver ritualContextResolver = new VampiricRitualContextResolver(progressionAccess);
        VampiricRitualFeedbackService ritualFeedbackService = new VampiricRitualFeedbackService();
        VampiricRitualSelectionService ritualSelectionService = new VampiricRitualSelectionService();
        SkillRequirementEvaluator skillRequirementEvaluator = new SkillRequirementEvaluator(
                progressionDefinitionProvider,
                skillConditionEvaluator,
                () -> progressionAccess,
                masqueradeHeatService,
                ritualService);
        SkillActionExecutor skillActionExecutor = new SkillActionExecutor(
                progressionDefinitionProvider,
                skillConditionEvaluator,
                skillRequirementEvaluator,
                temporaryModifiers,
                masqueradeHeatService);

        RelicInventoryService.init(RelicInventoryConfig.defaultSections("VampirismRelic"));
        RelicPresetProjectionService.init(RelicPresetProjectionConfig.defaultSections(
                "VampirismRelicPresetProxy",
                "VampirismRelicPresetProxy",
                "VampirismRelicPresetIndex"));
        RelicBindingService.init(
                skillRuntimeBootstrap.runtimeBindings()::snapshot,
                progressionDefinitionProvider,
                progressionAccess,
                CooldownTrackerAbilityCooldownAccess.instance(),
                PlayerRelicBindingsStore.instance(),
                (uuid, slot) -> VampirePlayerStateStore.get().isInfected(uuid)
                        ? java.util.Optional.of(VampireInfectionSystem.BLOOD_SUCKER_ABILITY_ID)
                        : java.util.Optional.empty());
        VampiricRelicSetService.init(progressionAccess);

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
                highestPositionSupplier,
                skillTreePresenter,
                skillTreeManager,
                lineageService,
                ritualService,
                masqueradeHeatService);
        StandardRelicUiAdapter relicUiAdapter = new StandardRelicUiAdapter(
                progressionDefinitionProvider,
                () -> VampirismConfig.get().getCooldownHudUpdateIntervalMs(),
                RelicInventoryService.itemId());
        VampirismSettingsUiAdapter settingsUiAdapter = new VampirismSettingsUiAdapter();
        VampirismProgressionPageFactory vampirismProgressionPageFactory = new VampirismProgressionPageFactory(
                VampirismUiPaths.theme(),
                skillTreeUiAdapter,
                relicUiAdapter,
                settingsUiAdapter,
                ritualService,
                ritualContextResolver,
                lineageService,
                masqueradeHeatService);
        ProgressionPageFactory progressionPageFactory = vampirismProgressionPageFactory;

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
        HudBackendResolver hudBackendResolver = HudBackendResolver.createDefault();
        SingleSlotHudCoordinator singleSlotHudCoordinator = new SingleSlotHudCoordinator();
        BloodHudService.init(
                hudBackendResolver,
                singleSlotHudCoordinator,
                BloodGaugeHud::new,
                (playerRef, hudKey) -> new ProgressionRelicCooldownHud(playerRef, hudKey, VampirismUiPaths.theme(), relicUiAdapter));
        NightHuntHudService.init(hudBackendResolver, singleSlotHudCoordinator, NightHuntStatusHud::new);
        RitualHudService.init(hudBackendResolver, singleSlotHudCoordinator, RitualStatusHud::new);
        AbilityService abilityService = new AbilityService(skillRequirementEvaluator, skillActionExecutor);
        abilityService.init(
                progressionDefinitionProvider,
                progressionAccess,
                VampireInfectionSystem::allowsTemporaryAbility,
                new VampireVitalityAbilityResourcePort(),
                (ctx, abilityId) -> triggerDispatcher.dispatch(TriggerEvent.onActivate(ctx, abilityId)));
        skillActionExecutor.setAbilityActivator(abilityService::activateFromAction);
        VampiricRitualSystem ritualVisualSystem = new VampiricRitualSystem(
                ritualRuntimeService,
                ritualContextResolver,
                ritualFeedbackService,
                ritualSelectionService);
        VampirismRitualToolActions ritualToolActions = new VampirismRitualToolActions(
                ritualService,
                ritualRuntimeService,
                ritualTemplateRegistry,
                ritualContextResolver,
                ritualVisualSystem,
                ritualFeedbackService,
                ritualSelectionService);
        VampirismInteractionRuntime.install(new VampirismInteractionRuntime.Services(
                ritualToolActions,
                abilityService,
                progressionPageFactory));

        VampiricSystemsSupport.configure(
                VampirismConfig.get(),
                page -> page instanceof SkillTreeUI);

        NightHuntProgressionRegistry.init();
        NightHuntSpawnRegistry.init();
        SunburnSystem.registerModifiers();
        VampireVitalitySystem.registerModifiers();
        VampireMovementSystem.registerModifiers(temporaryModifiers);
        VampireCombatSystem.registerModifiers();
        RelicPresetSelectionAdapter relicPresetSelectionAdapter = new RelicPresetSelectionAdapter();
        relicPresetSelectionAdapter.init();

        VampirismRuntime runtime = new VampirismRuntime(
                playerSkillRegistry,
                VampiricPlayerRuntimeCleanupService.create(new VampiricPlayerRuntimeCleanupService.CleanupPlan(
                        VampirismRuntime::cleanupProjectedRelicInventory,
                        (uuid, playerRef) -> MorphFlySystem.captureDisconnectState(uuid),
                        (uuid, playerRef) -> VampireVitalitySystem.captureDisconnectState(uuid),
                        (uuid, playerRef) -> {
                            VampireVitalitySystem.clearPlayer(uuid);
                            VampiricRelicSetService.clearPlayer(uuid);
                            lineageService.clearPlayer(uuid);
                        },
                        (uuid, playerRef) -> nightHuntService.captureDisconnectState(uuid),
                        (uuid, playerRef) -> SkillTreeManager.get().evictPlayer(uuid),
                        (uuid, playerRef) -> ModifierContext.REGISTRY.evict(uuid),
                        (uuid, playerRef) -> EffectModifierSystem.clearPlayer(uuid),
                        (uuid, playerRef) -> playerSkillRegistry.captureDisconnectState(uuid),
                        (uuid, playerRef) -> MorphFlySystem.clearTransientState(uuid),
                        (uuid, playerRef) -> FormHealthSystem.clearPlayer(uuid),
                        (uuid, playerRef) -> BloodFeedSystem.clearPlayer(uuid),
                        (uuid, playerRef) -> {
                            BloodConversionSystem.clearPlayer(uuid);
                            ritualRuntimeService.capturePersistedState(uuid);
                            NightHuntHudService.cleanup(playerRef);
                            RitualHudService.cleanup(playerRef);
                            ritualVisualSystem.suspendPlayer(uuid);
                            ritualSelectionService.clearPlayer(uuid);
                            var familiar = VampiricRitualCompanionTracker.clearPlayer(uuid);
                            if (familiar != null && familiar.companionRef().isValid()) {
                                Store<EntityStore> store = familiar.companionRef().getStore();
                                if (store != null) {
                                    store.removeEntity(familiar.companionRef(), RemoveReason.REMOVE);
                                }
                            }
                        },
                        (uuid, playerRef) -> nightHuntService.clearPlayer(uuid),
                        (uuid, playerRef) -> SunburnSystem.onPlayerLeave(uuid),
                        (uuid, playerRef) -> SneakSystem.clearPlayer(uuid),
                        (uuid, playerRef) -> VampireMovementSystem.clearPlayer(uuid),
                        (uuid, playerRef) -> VampireCombatSystem.clearPlayer(uuid),
                        (uuid, playerRef) -> CrimsonUmbrellaVisualSystem.clearPlayer(uuid),
                        (uuid, playerRef) -> VampireInfectionSystem.clearPlayer(uuid),
                        (uuid, playerRef) -> temporaryModifiers.clearPlayer(uuid),
                        (uuid, playerRef) -> PassiveEffectSystem.onPlayerDisconnect(
                                uuid,
                                passiveTriggerRuntimeService,
                                persistentPassiveEffectService))),
                nightHuntService,
                relicPresetSelectionAdapter,
                lineageService);
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
                skillRuntimeStateResolver,
                ritualVisualSystem);
        registerCommands(
                plugin,
                progressionAccess,
                nightHuntService,
                abilityService,
                progressionPageFactory,
                vampirismProgressionPageFactory,
                lineageService,
                masqueradeHeatService,
                ritualService,
                ritualRuntimeService,
                ritualContextResolver,
                runtime,
                ritualVisualSystem,
                ritualFeedbackService,
                ritualSelectionService,
                ritualTemplateRegistry);
        return runtime;
    }

    public void shutdown() {
        VampirismClassifications.unregisterProvider();
        relicPresetSelectionAdapter.shutdown();
        RitualOfferingSurfaceInteraction.clearRuntime();
        VampirismInteractionRuntime.clear();
    }

    private void registerPlayerLifecycle(@Nonnull Vampirism plugin) {
        plugin.getEventRegistry().register(PlayerConnectEvent.class, e -> onPlayerConnect(e.getPlayerRef().getUuid()));
        plugin.getEventRegistry().registerGlobal(PlayerReadyEvent.class, e -> MorphFlySystem.onPlayerReady(e.getPlayerRef()));
        plugin.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, e ->
                WorldMapTrackerAdapter.syncTransform(e.getHolder(), e.getWorld(), false));
        PlayerWorldLifecycleEventAdapter.registerWorldRemovalEvents(plugin.getEventRegistry(), plugin.getClassLoader());
        plugin.getEventRegistry().register(PlayerDisconnectEvent.class, e ->
                onPlayerDisconnect(e.getPlayerRef().getUuid(), e.getPlayerRef().getReference()));
    }

    private void onPlayerConnect(@Nonnull UUID uuid) {
        connectedPlayers.add(uuid);
        playerSkillRegistry.restoreRuntimeState(uuid);
        nightHuntService.onPlayerConnect(uuid);
        EffectModifierSystem.clearPlayer(uuid);
        SkillTreeManager.get().reloadModifiers(uuid);
        lineageService.syncModifiers(uuid);
        // Passive connect-time effects are applied lazily by PassiveEffectSystem once the
        // player's entity context is fully available (typically within the first tick).
    }

    private void onPlayerDisconnect(@Nonnull UUID uuid, @Nullable Ref<EntityStore> playerRef) {
        connectedPlayers.remove(uuid);
        playerRuntimeCleanupCoordinator.cleanup(uuid, playerRef);
    }

    @Nonnull
    public ReloadRuntimeStateSummary refreshReloadedJsonState(@Nonnull VampiricRitualRuntimeService ritualRuntimeService,
                                                              @Nonnull VampiricRitualContextResolver ritualContextResolver) {
        int refreshedPlayers = 0;
        int abortedRituals = 0;
        int clearedAssemblies = 0;
        for (UUID uuid : new LinkedHashSet<>(connectedPlayers)) {
            Optional<VampiricRitualRuntimeSnapshot> ritualSnapshot = ritualRuntimeService.snapshot(uuid);
            if (ritualSnapshot.isPresent()) {
                if (ritualSnapshot.get().active()) {
                    abortedRituals++;
                } else {
                    clearedAssemblies++;
                }
                abortRuntimeRitual(uuid, VampireVitalitySystem.getRefByUuid(uuid), ritualRuntimeService, ritualContextResolver);
            }
            EffectModifierSystem.clearPlayer(uuid);
            SkillTreeManager.get().reloadModifiers(uuid);
            lineageService.syncModifiers(uuid);
            refreshedPlayers++;
        }
        return new ReloadRuntimeStateSummary(refreshedPlayers, abortedRituals, clearedAssemblies);
    }

    private static void registerCommands(@Nonnull Vampirism plugin,
                                         @Nonnull VampirismSkillProgressionAccess progressionAccess,
                                         @Nonnull NightHuntService nightHuntService,
                                         @Nonnull AbilityService abilityService,
                                         @Nonnull ProgressionPageFactory progressionPageFactory,
                                         @Nonnull VampirismProgressionPageFactory vampirismProgressionPageFactory,
                                         @Nonnull VampiricLineageService lineageService,
                                         @Nonnull MasqueradeHeatService masqueradeHeatService,
                                         @Nonnull VampiricRitualService ritualService,
                                         @Nonnull VampiricRitualRuntimeService ritualRuntimeService,
                                         @Nonnull VampiricRitualContextResolver ritualContextResolver,
                                         @Nonnull VampirismRuntime runtime,
                                         @Nonnull VampiricRitualSystem ritualVisualSystem,
                                         @Nonnull VampiricRitualFeedbackService ritualFeedbackService,
                                         @Nonnull VampiricRitualSelectionService ritualSelectionService,
                                         @Nonnull VampiricRitualTemplateRegistry ritualTemplateRegistry) {
        plugin.getCommandRegistry().registerCommand(
                new VampirismCommand(
                        plugin,
                        progressionAccess,
                        nightHuntService,
                        abilityService,
                        lineageService,
                        masqueradeHeatService,
                        ritualService,
                        ritualRuntimeService,
                        ritualContextResolver,
                        runtime));
        plugin.getCommandRegistry().registerCommand(new VampirismSkillTreeCommand(progressionPageFactory));
        plugin.getCommandRegistry().registerCommand(new VampirismHuntCompendiumCommand(vampirismProgressionPageFactory));
        plugin.getCommandRegistry().registerCommand(new VampirismPotionCommand());
        plugin.getCommandRegistry().registerCommand(new VampirismRelicCommand(abilityService));
        plugin.getCommandRegistry().registerCommand(
                new VampirismRitualCommand(
                        ritualService,
                        ritualRuntimeService,
                        ritualTemplateRegistry,
                        ritualContextResolver,
                        ritualVisualSystem,
                        ritualFeedbackService,
                        ritualSelectionService));
        plugin.getCommandRegistry().registerCommand(new VampirismRelicBindingsCommand(progressionPageFactory));
        plugin.getCommandRegistry().registerCommand(new VampirismRelicBindingCommand(progressionPageFactory));
    }

    public record ReloadRuntimeStateSummary(int refreshedPlayers,
                                            int abortedRituals,
                                            int clearedAssemblies) {
    }

    private static void registerSystems(@Nonnull Vampirism plugin,
                                        @Nonnull PassiveService passiveService,
                                        @Nonnull PassiveTriggerRuntimeService<SkillRuntimeContext> passiveTriggerRuntimeService,
                                        @Nonnull PersistentPassiveEffectService<SkillRuntimeContext> persistentPassiveEffectService,
                                        @Nonnull ProgressionDefinitionProvider progressionDefinitionProvider,
                                        @Nonnull VampirismSkillProgressionAccess progressionAccess,
                                        @Nonnull ModifierScopeMatcher modifierScopeMatcher,
                                        @Nonnull NightHuntService nightHuntService,
                                        @Nonnull SkillRuntimeStateResolver<ModifierContext, SkillRuntimeContext> skillRuntimeStateResolver,
                                        @Nonnull VampiricRitualSystem ritualVisualSystem) {
        plugin.getEntityStoreRegistry().registerSystem(new VampireInfectionSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireVitalitySystem());
        plugin.getEntityStoreRegistry().registerSystem(new BloodFeedSystem(progressionAccess));
        plugin.getEntityStoreRegistry().registerSystem(new BloodConversionSystem());
        plugin.getEntityStoreRegistry().registerSystem(ritualVisualSystem);
        plugin.getEntityStoreRegistry().registerSystem(new VampiricRitualCompanionSystem());
        plugin.getEntityStoreRegistry().registerSystem(new VampireCombatSystem(
                passiveService,
                new VampireCombatSystem.NightHuntCombatSupport() {
                    @Override
                    public void recordMarkedPreyHit(@Nullable UUID attackerUuid,
                                                    @Nonnull Ref<EntityStore> preyRef,
                                                    @Nonnull Store<EntityStore> store) {
                        nightHuntService.recordMarkedPreyHit(attackerUuid, preyRef, store);
                    }

                    @Override
                    public void onPlayerKilledMarkedPrey(@Nullable UUID attackerUuid,
                                                         @Nonnull Ref<EntityStore> attackerRef,
                                                         @Nonnull Ref<EntityStore> preyRef,
                                                         @Nonnull Store<EntityStore> store) {
                        if (nightHuntService.onPlayerKilledMarkedPrey(attackerUuid, attackerRef, preyRef, store)) {
                            VampirismPlayerFeedback.notifyMarkedPreyKilled(attackerRef, store);
                        }
                    }
                },
                skillRuntimeStateResolver));
        plugin.getEntityStoreRegistry().registerSystem(new VampireMovementSystem());
        plugin.getEntityStoreRegistry().registerSystem(new EffectModifierSystem(modifierScopeMatcher));
        plugin.getEntityStoreRegistry().registerSystem(new FormHealthSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SunburnSystem());
        plugin.getEntityStoreRegistry().registerSystem(new SneakSystem());
        plugin.getEntityStoreRegistry().registerSystem(new MorphFlySystem());
        plugin.getEntityStoreRegistry().registerSystem(new CrimsonUmbrellaVisualSystem());
        plugin.getEntityStoreRegistry().registerSystem(new NightMarkedVictimSystem(progressionAccess));
        plugin.getEntityStoreRegistry().registerSystem(new NightHuntHudSystem(nightHuntService));
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

    private static void cleanupProjectedRelicInventory(@Nonnull UUID uuid,
                                                       @Nullable Ref<EntityStore> playerRef) {
        if (playerRef == null) {
            RelicPresetProjectionService.clearPlayer(uuid);
            return;
        }
        var store = playerRef.getStore();
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

    private static void abortRuntimeRitual(@Nonnull UUID uuid,
                                           @Nullable Ref<EntityStore> playerRef,
                                           @Nonnull VampiricRitualRuntimeService ritualRuntimeService,
                                           @Nonnull VampiricRitualContextResolver ritualContextResolver) {
        Optional<VampiricRitualRuntimeSnapshot> snapshot = ritualRuntimeService.snapshot(uuid);
        if (snapshot.isEmpty()) {
            if (playerRef != null) {
                Store<EntityStore> store = playerRef.getStore();
                if (store != null && !store.isShutdown()) {
                    var ritualContext = ritualContextResolver.buildContext(
                            uuid,
                            store,
                            Set.of(VampiricRitualRegistry.TAG_ANCIENT_COFFIN));
                    VampiricRitualOfferingRecoveryService.dropRecoveredOfferings(
                            ritualRuntimeService.discardPersistedState(uuid, ritualContext),
                            store);
                    ritualRuntimeService.clearPlayer(uuid);
                    return;
                }
            }
            ritualRuntimeService.clearPlayer(uuid);
            ritualRuntimeService.clearPersistedState(uuid);
            return;
        }
        if (playerRef == null) {
            ritualRuntimeService.clearPlayer(uuid);
            ritualRuntimeService.clearPersistedState(uuid);
            return;
        }
        Store<EntityStore> store = playerRef.getStore();
        if (store == null || store.isShutdown()) {
            ritualRuntimeService.clearPlayer(uuid);
            ritualRuntimeService.clearPersistedState(uuid);
            return;
        }
        Runnable action = () -> {
            if (store.isShutdown()) {
                ritualRuntimeService.clearPlayer(uuid);
                ritualRuntimeService.clearPersistedState(uuid);
                return;
            }
            var ritualContext = ritualContextResolver.buildContext(
                    uuid,
                    store,
                    Set.of(VampiricRitualRegistry.TAG_ANCIENT_COFFIN));
            VampiricRitualOfferingRecoveryService.dropRecoveredOfferings(
                    ritualRuntimeService.abort(uuid, ritualContext).offeringRecovery(),
                    store);
            VampiricRitualOfferingRecoveryService.dropRecoveredOfferings(
                    ritualRuntimeService.discardPersistedState(uuid, ritualContext),
                    store);
            ritualRuntimeService.clearPlayer(uuid);
        };
        World world = WorldStoreAdapter.resolveWorld(store);
        if (world != null) {
            if (world.isInThread()) {
                action.run();
                return;
            }
            world.execute(action);
            return;
        }
        if (store.isInThread()) {
            action.run();
            return;
        }
        ritualRuntimeService.clearPlayer(uuid);
        ritualRuntimeService.clearPersistedState(uuid);
    }
}
