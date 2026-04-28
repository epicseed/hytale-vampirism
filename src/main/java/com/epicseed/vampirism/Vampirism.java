package com.epicseed.vampirism;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.commands.VampirismCommand;
import com.epicseed.vampirism.commands.VampirismPotionCommand;
import com.epicseed.vampirism.commands.VampirismSkillTreeCommand;
import com.epicseed.vampirism.commands.VampirismRelicCommand;
import com.epicseed.vampirism.commands.VampirismRelicBindingsCommand;
import com.epicseed.vampirism.commands.VampirismRelicBindingCommand;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.TemporaryModifierTracker;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.registry.PlayerRelicBindings;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.skill.data.SkillLoader;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.ModifierDefRegistry;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.registry.ReusableDefRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.registry.StateRegistry;
import com.epicseed.vampirism.skill.registry.StatDefRegistry;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.epicseed.vampirism.systems.BloodFeedSystem;
import com.epicseed.vampirism.systems.BloodConversionSystem;
import com.epicseed.vampirism.systems.CrimsonUmbrellaVisualSystem;
import com.epicseed.vampirism.systems.VampireCombatSystem;
import com.epicseed.vampirism.systems.EffectModifierSystem;
import com.epicseed.vampirism.systems.FormHealthSystem;
import com.epicseed.vampirism.systems.MorphFlySystem;
import com.epicseed.vampirism.systems.NightMarkedVictimSystem;
import com.epicseed.vampirism.systems.PassiveEffectSystem;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.epicseed.vampirism.systems.VampireMovementSystem;
import com.epicseed.vampirism.systems.RelicChestLockSystem;
import com.epicseed.vampirism.systems.RelicDeathDropPreventSystem;
import com.epicseed.vampirism.systems.RelicDropPreventSystem;
import com.epicseed.vampirism.systems.SneakSystem;
import com.epicseed.vampirism.systems.SunburnSystem;
import com.epicseed.vampirism.systems.VampireSleepSystem;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector2d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.AddPlayerToWorldEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class Vampirism extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Field WORLD_MAP_TRACKER_TRANSFORM_FIELD = resolveWorldMapTrackerTransformField();

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
    private Vector2d highestPosition;

    public Vampirism(@Nonnull JavaPluginInit init) {
        super(init);
        instance = this;

        Config<VampirismConfig> config = this.withConfig("Vampirism", VampirismConfig.CODEC);
        VampirismConfig.init(config);

        RegisterSkills();
    }

    @Override
    protected void setup() {
        VampireStatusRegistry.init(this.getDataDirectory());
        PlayerRelicBindings.init(this.getDataDirectory());
        PlayerSkillRegistry.init(this.getDataDirectory());
        NightHuntSpawnRegistry.init();
        PassiveService.init();
        this.skillTreeManager = new SkillTreeManager(skillRegistry);
        SunburnSystem.registerModifiers();
        VampireVitalitySystem.registerModifiers();
        VampireMovementSystem.registerModifiers();
        VampireCombatSystem.registerModifiers();

        this.getEventRegistry().register(PlayerConnectEvent.class, e -> {
            UUID uuid = e.getPlayerRef().getUuid();
            PlayerSkillRegistry.get().onPlayerConnect(uuid);
            NightMarkedVictimSystem.onPlayerConnect(uuid);
            AbilityCooldownTracker.restorePlayer(uuid, PlayerSkillRegistry.get().getPersistedAbilityCooldowns(uuid));
            EffectModifierSystem.clearPlayer(uuid);
            SkillTreeManager.get().reloadModifiers(uuid);
            // Passive connect-time effects are applied lazily by PassiveEffectSystem once the
            // player's entity context is fully available (typically within the first tick).
        });
        this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, e -> {
            MorphFlySystem.onPlayerReady(e.getPlayerRef());
        });
        this.getEventRegistry().registerGlobal(AddPlayerToWorldEvent.class, e -> {
            syncWorldMapTrackerTransform(e.getHolder(), e.getWorld(), false);
        });
        this.getEventRegistry().registerGlobal(RemovedPlayerFromWorldEvent.class, e -> {
            syncWorldMapTrackerTransform(e.getHolder(), e.getWorld(), true);
        });
        this.getEventRegistry().register(PlayerDisconnectEvent.class, e -> {
            UUID uuid = e.getPlayerRef().getUuid();
            MorphFlySystem.captureDisconnectState(uuid);
            VampireVitalitySystem.captureDisconnectState(uuid);
            PlayerSkillRegistry.get().setPersistedAbilityCooldowns(uuid, AbilityCooldownTracker.snapshotRemaining(uuid));
            NightMarkedVictimSystem.captureDisconnectState(uuid);
            SkillTreeManager.get().evictPlayer(uuid);
            ModifierRegistry.get().evict(uuid);
            EffectModifierSystem.clearPlayer(uuid);
            PlayerSkillRegistry.get().onPlayerDisconnect(uuid);
            MorphFlySystem.clearTransientState(uuid);
            FormHealthSystem.clearPlayer(uuid);
            BloodFeedSystem.clearPlayer(uuid);
            BloodConversionSystem.clearPlayer(uuid);
            NightMarkedVictimSystem.clearPlayer(uuid);
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

        LOGGER.atInfo().log("[Vampirism] Registering systems and commands...");

        this.getEntityStoreRegistry().registerSystem(new VampireInfectionSystem());
        this.getEntityStoreRegistry().registerSystem(new VampireVitalitySystem());
        this.getEntityStoreRegistry().registerSystem(new BloodFeedSystem());
        this.getEntityStoreRegistry().registerSystem(new BloodConversionSystem());
        this.getEntityStoreRegistry().registerSystem(new VampireCombatSystem());
        this.getEntityStoreRegistry().registerSystem(new VampireMovementSystem());
        this.getEntityStoreRegistry().registerSystem(new EffectModifierSystem());
        this.getEntityStoreRegistry().registerSystem(new FormHealthSystem());
        this.getEntityStoreRegistry().registerSystem(new SunburnSystem());
        this.getEntityStoreRegistry().registerSystem(new SneakSystem());
        this.getEntityStoreRegistry().registerSystem(new MorphFlySystem());
        this.getEntityStoreRegistry().registerSystem(new CrimsonUmbrellaVisualSystem());
        this.getEntityStoreRegistry().registerSystem(new NightMarkedVictimSystem());
        this.getEntityStoreRegistry().registerSystem(new PassiveEffectSystem());
        this.getEntityStoreRegistry().registerSystem(new RelicDropPreventSystem());
        this.getEntityStoreRegistry().registerSystem(new RelicDeathDropPreventSystem());
        this.getEntityStoreRegistry().registerSystem(new RelicChestLockSystem());
        this.getEntityStoreRegistry().registerSystem(new VampireSleepSystem());
        this.getCommandRegistry().registerCommand(new VampirismCommand());
        this.getCommandRegistry().registerCommand(new VampirismSkillTreeCommand());
        this.getCommandRegistry().registerCommand(new VampirismPotionCommand());
        this.getCommandRegistry().registerCommand(new VampirismRelicCommand());
        this.getCommandRegistry().registerCommand(new VampirismRelicBindingsCommand());
        this.getCommandRegistry().registerCommand(new VampirismRelicBindingCommand());

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

        SkillLoader skillLoader = new SkillLoader();

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

    private static void syncWorldMapTrackerTransform(@Nonnull Holder<EntityStore> holder,
                                                     @Nonnull World world,
                                                     boolean clear) {
        Runnable action = () -> {
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) {
                return;
            }
            TransformComponent transform = clear
                    ? null
                    : holder.getComponent(TransformComponent.getComponentType());
            setWorldMapTrackerTransform(player.getWorldMapTracker(), transform);
        };
        if (world.isInThread()) {
            action.run();
            return;
        }
        world.execute(action);
    }

    // WorldMapTracker lazily grabs the player's transform from its own map thread; prime it on WorldThread instead.
    private static void setWorldMapTrackerTransform(@Nonnull WorldMapTracker tracker,
                                                    TransformComponent transform) {
        try {
            WORLD_MAP_TRACKER_TRANSFORM_FIELD.set(tracker, transform);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to synchronize WorldMapTracker transform cache.", e);
        }
    }

    @Nonnull
    private static Field resolveWorldMapTrackerTransformField() {
        try {
            Field field = WorldMapTracker.class.getDeclaredField("transformComponent");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    protected void shutdown() {
        LOGGER.atInfo().log("[Vampirism] Plugin disabled.");
    }

    public static Vampirism getInstance() { return instance; }

    public Vector2d GetHighestPosition()            { return highestPosition; }
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
