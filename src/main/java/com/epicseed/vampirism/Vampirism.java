package com.epicseed.vampirism;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.bootstrap.CommandRegistrar;
import com.epicseed.vampirism.bootstrap.PlayerLifecycleCoordinator;
import com.epicseed.vampirism.bootstrap.SystemRegistrar;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.blood.BloodHudService;
import com.epicseed.vampirism.domain.blood.FeedCompletionService;
import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.epicseed.vampirism.domain.skill.SkillTreePresenter;
import com.epicseed.vampirism.hud.RelicCooldownHud;
import com.epicseed.vampirism.hytale.RelicPresetSelectionAdapter;
import com.epicseed.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.registry.PlayerRelicBindings;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.runtime.ProgressionLifecycleService;
import com.epicseed.vampirism.skill.data.SkillLoader;
import com.epicseed.vampirism.skill.data.SkillDataPaths;
import com.epicseed.vampirism.skill.data.VampirismSkillDataLoadHooks;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeBindingsHolder;
import com.epicseed.vampirism.skill.runtime.RegistryBackedReusableDefinitionProvider;
import com.epicseed.vampirism.skill.runtime.AbilityService;
import com.epicseed.vampirism.skill.runtime.CooldownTrackerAbilityCooldownAccess;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeStateResolver;
import com.epicseed.vampirism.skill.runtime.TriggerDispatcher;
import com.epicseed.vampirism.skill.runtime.VampireVitalityAbilityResourcePort;
import com.epicseed.vampirism.skill.runtime.VampirismAbilityAccessProvider;
import com.epicseed.vampirism.skill.runtime.PlayerRegistrySkillProgressionAccess;
import com.epicseed.vampirism.skill.runtime.PassiveTriggerRuntimeService;
import com.epicseed.vampirism.skill.runtime.SkillRequirementEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillActionExecutor;
import com.epicseed.vampirism.skill.runtime.VampirismProgressionDefinitionProvider;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.ModifierDefRegistry;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.registry.ReusableDefRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.registry.StateRegistry;
import com.epicseed.vampirism.skill.registry.StatDefRegistry;
import com.epicseed.vampirism.systems.VampireCombatSystem;
import com.epicseed.vampirism.systems.VampireMovementSystem;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.ui.VampirismProgressionPageFactory;
import com.epicseed.vampirism.ui.VampirismRelicUiAdapter;
import com.epicseed.vampirism.ui.VampirismSkillTreeUiAdapter;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class Vampirism extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static Vampirism instance;

    private SkillRegistry skillRegistry;
    private PassiveRegistry passiveRegistry;
    private AbilityRegistry abilityRegistry;
    private ModifierDefRegistry modifierDefRegistry;
    private EffectDefRegistry effectDefRegistry;
    private StateRegistry stateRegistry;
    private StatDefRegistry statDefRegistry;
    private ReusableDefRegistry conditionRegistry;
    private ReusableDefRegistry requirementRegistry;
    private ReusableDefRegistry triggerRegistry;
    private ReusableDefRegistry actionRegistry;
    private ReusableDefRegistry targetingRegistry;
    private SkillTreeManager skillTreeManager;
    private VampirismProgressionDefinitionProvider progressionDefinitionProvider;
    private PlayerRegistrySkillProgressionAccess progressionAccess;
    private VampirismProgressionPageFactory progressionPageFactory;
    private PassiveService passiveService;
    private Vector2d highestPosition;
    private final SkillRuntimeBindingsHolder runtimeBindings = new SkillRuntimeBindingsHolder();

    public Vampirism(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;

        Config<VampirismConfig> config = this.withConfig("Vampirism", VampirismConfig.CODEC);
        VampirismConfig.init(config);
        SkillRuntimeStateResolver.init(runtimeBindings::snapshot);

        RegisterSkills();
    }

    @Override
    protected void setup() {
        VampireStatusRegistry.init(this.getDataDirectory());
        PlayerSkillRegistry playerSkillRegistry = PlayerSkillRegistry.init(this.getDataDirectory());
        VampirismClassifications.registerProvider();
        PlayerRegistrySkillProgressionAccess.init(playerSkillRegistry);
        ProgressionLifecycleService.init(playerSkillRegistry);
        PlayerRelicBindings.init(playerSkillRegistry);
        this.progressionDefinitionProvider = VampirismProgressionDefinitionProvider.instance();
        this.progressionAccess = PlayerRegistrySkillProgressionAccess.instance();
        SkillRequirementEvaluator.init(progressionAccess);
        RelicBindingService.init(
                runtimeBindings::snapshot,
                progressionDefinitionProvider,
                progressionAccess,
                CooldownTrackerAbilityCooldownAccess.instance());
        this.skillTreeManager = new SkillTreeManager(progressionDefinitionProvider, progressionAccess);
        SkillTreePresenter skillTreePresenter = new SkillTreePresenter(progressionDefinitionProvider, progressionAccess);
        VampirismSkillTreeUiAdapter skillTreeUiAdapter = new VampirismSkillTreeUiAdapter(
                progressionDefinitionProvider,
                progressionAccess,
                this::GetHighestPosition,
                skillTreePresenter,
                skillTreeManager);
        VampirismRelicUiAdapter relicUiAdapter = new VampirismRelicUiAdapter(
                progressionDefinitionProvider,
                () -> VampirismConfig.get().getCooldownHudUpdateIntervalMs());
        this.progressionPageFactory = new VampirismProgressionPageFactory(
                VampirismUiPaths.theme(),
                skillTreeUiAdapter,
                relicUiAdapter);
        TriggerDispatcher triggerDispatcher = new TriggerDispatcher(progressionDefinitionProvider, progressionAccess);
        this.passiveService = new PassiveService(progressionDefinitionProvider, progressionAccess, triggerDispatcher::dispatch);
        PassiveTriggerRuntimeService.init(triggerDispatcher::dispatch);
        SkillActionExecutor.init(passiveService::onFeed);
        FeedCompletionService.init(passiveService::onFeed);
        BloodHudService.init(playerRef -> new RelicCooldownHud(playerRef, VampirismUiPaths.theme(), relicUiAdapter));
        AbilityService.init(
                progressionDefinitionProvider,
                new VampirismAbilityAccessProvider(playerSkillRegistry),
                new VampireVitalityAbilityResourcePort(),
                (ctx, abilityId) -> triggerDispatcher.dispatch(com.epicseed.vampirism.skill.runtime.TriggerEvent.onActivate(ctx, abilityId)));
        NightHuntSpawnRegistry.init();
        SunburnSystem.registerModifiers();
        VampireVitalitySystem.registerModifiers();
        VampireMovementSystem.registerModifiers();
        VampireCombatSystem.registerModifiers();

        PlayerLifecycleCoordinator.register(this);
        RelicPresetSelectionAdapter.init();

        LOGGER.atInfo().log("[Vampirism] Registering systems and commands...");

        SystemRegistrar.register(this);
        CommandRegistrar.register(this);

        LOGGER.atInfo().log("[Vampirism] All systems registered.");
    }

    private void RegisterSkills() {
        int maxX = 0;
        int maxY = 0;

        this.skillRegistry       = new SkillRegistry();
        this.passiveRegistry     = new PassiveRegistry();
        this.abilityRegistry     = new AbilityRegistry();
        this.modifierDefRegistry = new ModifierDefRegistry();
        this.effectDefRegistry   = new EffectDefRegistry();
        this.stateRegistry       = new StateRegistry();
        this.statDefRegistry     = new StatDefRegistry();
        this.conditionRegistry   = new ReusableDefRegistry();
        this.requirementRegistry = new ReusableDefRegistry();
        this.triggerRegistry     = new ReusableDefRegistry();
        this.actionRegistry      = new ReusableDefRegistry();
        this.targetingRegistry   = new ReusableDefRegistry();
        VampirismProgressionDefinitionProvider.init(
                skillRegistry,
                passiveRegistry,
                abilityRegistry,
                effectDefRegistry);
        SkillRuntimeDefinitions.init(new RegistryBackedReusableDefinitionProvider(
                conditionRegistry,
                requirementRegistry,
                triggerRegistry,
                actionRegistry,
                targetingRegistry));

        SkillLoader skillLoader = new SkillLoader(
                SkillDataPaths.vampirismDefaults(),
                new VampirismSkillDataLoadHooks(runtimeBindings));

        List<Skill> skills = skillLoader.LoadSkills(skillRegistry);
        for (Skill skill : skills) {
            this.getLogger().at(Level.INFO).log(String.format("Skills Loaded %s", skill.id));
            if (Math.abs(skill.position.x) > Math.abs(maxX)) maxX = skill.position.x;
            if (Math.abs(skill.position.y) > Math.abs(maxY)) maxY = skill.position.y;
        }

        skillLoader.LoadDefinitions(
                passiveRegistry, abilityRegistry, modifierDefRegistry,
                effectDefRegistry, stateRegistry, statDefRegistry,
                conditionRegistry, requirementRegistry, triggerRegistry,
                actionRegistry, targetingRegistry);

        LOGGER.atInfo().log(String.format("[Vampirism] Loaded %d passives, %d abilities, %d modifiers, %d effects, %d states, %d stats, %d conditions, %d requirements, %d triggers, %d actions, %d targetings.",
                passiveRegistry.GetAll().size(),
                abilityRegistry.GetAll().size(),
                modifierDefRegistry.GetAll().size(),
                effectDefRegistry.GetAll().size(),
                stateRegistry.GetAll().size(),
                statDefRegistry.GetAll().size(),
                conditionRegistry.GetAll().size(),
                requirementRegistry.GetAll().size(),
                triggerRegistry.GetAll().size(),
                actionRegistry.GetAll().size(),
                targetingRegistry.GetAll().size()));

        SetHighestPositions(maxX, maxY);
    }

    private void SetHighestPositions(int maxX, int maxY) {
        highestPosition = new Vector2d(Math.abs(maxX), Math.abs(maxY));
    }

    @Override
    protected void shutdown() {
        VampirismClassifications.unregisterProvider();
        RelicPresetSelectionAdapter.shutdown();
        LOGGER.atInfo().log("[Vampirism] Plugin disabled.");
    }

    public static Vampirism getInstance() { return instance; }

    public Vector2d GetHighestPosition()            { return highestPosition; }
    public VampirismProgressionDefinitionProvider getProgressionDefinitionProvider() { return progressionDefinitionProvider; }
    public PlayerRegistrySkillProgressionAccess getProgressionAccess() { return progressionAccess; }
    public VampirismProgressionPageFactory getProgressionPageFactory() { return progressionPageFactory; }
    public PassiveService getPassiveService()       { return passiveService; }
    public SkillRegistry GetSkillRegistry()         { return skillRegistry; }
    public PassiveRegistry GetPassiveRegistry()     { return passiveRegistry; }
    public AbilityRegistry GetAbilityRegistry()    { return abilityRegistry; }
    public ModifierDefRegistry GetModifierDefRegistry() { return modifierDefRegistry; }
    public EffectDefRegistry GetEffectDefRegistry() { return effectDefRegistry; }
    public StateRegistry GetStateRegistry()         { return stateRegistry; }
    public StatDefRegistry GetStatDefRegistry()     { return statDefRegistry; }
    public ReusableDefRegistry GetConditionRegistry()   { return conditionRegistry; }
    public ReusableDefRegistry GetRequirementRegistry() { return requirementRegistry; }
    public ReusableDefRegistry GetTriggerRegistry()     { return triggerRegistry; }
    public ReusableDefRegistry GetActionRegistry()      { return actionRegistry; }
    public ReusableDefRegistry GetTargetingRegistry()   { return targetingRegistry; }
    public SkillTreeManager GetSkillTreeManager()   { return skillTreeManager; }
}
