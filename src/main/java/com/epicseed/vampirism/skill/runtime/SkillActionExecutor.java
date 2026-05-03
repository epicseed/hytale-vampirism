package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.runtime.SkillActivationResult;
import com.epicseed.epiccore.skill.runtime.actions.ActionHandlerPack;
import com.epicseed.epiccore.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.epiccore.skill.runtime.actions.RuntimeActionExecutors;
import com.epicseed.epiccore.skill.runtime.actions.StandardActionPacks;
import com.epicseed.epiccore.skill.runtime.actions.StandardActionSupports;
import com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker;
import com.epicseed.vampirism.skill.runtime.actions.BloodActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ChannelActionHandlers;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.util.WorldPositionHelper;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.World;

public final class SkillActionExecutor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final StandardActionPacks.CombatActionPackOptions STANDARD_COMBAT_OPTIONS =
            new StandardActionPacks.CombatActionPackOptions(
                    VampireStatType.PROJECTILE_DAMAGE.name(),
                    VampireStatType.SELF_DAMAGE_MULTIPLIER.name(),
                    VampireStatType.PROJECTILE_DAMAGE.name(),
                    VampireStatType.PROJECTILE_SPEED.name());
    private final ProgressionDefinitionProvider definitionProvider;
    private final SkillConditionEvaluator conditionEvaluator;
    private final SkillRequirementEvaluator requirementEvaluator;
    private final TemporaryModifierTracker<StatType> temporaryModifiers;
    private volatile Consumer<SkillRuntimeContext> onFinalBlow = ctx -> {};
    private volatile BiFunction<String, SkillRuntimeContext, SkillActivationResult> abilityActivator =
            (abilityId, ctx) -> SkillActivationResult.failed(
                    SkillActivationResult.Status.DENIED,
                    "Ability service not initialized.");
    private final com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor<SkillRuntimeContext> executor;

    public SkillActionExecutor(@Nonnull ProgressionDefinitionProvider definitionProvider,
                               @Nonnull SkillConditionEvaluator conditionEvaluator,
                               @Nonnull SkillRequirementEvaluator requirementEvaluator,
                               @Nonnull TemporaryModifierTracker<StatType> temporaryModifiers) {
        this.definitionProvider = Objects.requireNonNull(definitionProvider, "definitionProvider");
        this.conditionEvaluator = Objects.requireNonNull(conditionEvaluator, "conditionEvaluator");
        this.requirementEvaluator = Objects.requireNonNull(requirementEvaluator, "requirementEvaluator");
        this.temporaryModifiers = Objects.requireNonNull(temporaryModifiers, "temporaryModifiers");
        this.executor = RuntimeActionExecutors.create(
                standardActionSupport(),
                conditionEvaluator::evaluateAll,
                healthActionSupport(),
                combatActionSupport(),
                STANDARD_COMBAT_OPTIONS,
                temporaryModifierActionSupport(),
                vampirismActionExtensions());
    }

    public void setOnFinalBlow(@Nullable Consumer<SkillRuntimeContext> onFinalBlow) {
        this.onFinalBlow = onFinalBlow != null ? onFinalBlow : ctx -> {};
    }

    public void setAbilityActivator(@Nullable BiFunction<String, SkillRuntimeContext, SkillActivationResult> abilityActivator) {
        this.abilityActivator = abilityActivator != null
                ? abilityActivator
                : (abilityId, ctx) -> SkillActivationResult.failed(
                        SkillActivationResult.Status.DENIED,
                        "Ability service not initialized.");
    }

    public boolean executeAll(List<Map<String, Object>> actions, SkillRuntimeContext ctx) {
        return executor.executeAll(actions, ctx);
    }

    public boolean execute(Map<String, Object> action, SkillRuntimeContext ctx) {
        return executor.execute(action, ctx);
    }

    public com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor<SkillRuntimeContext> executor() {
        return executor;
    }

    ActionHandlerRegistry<SkillRuntimeContext> createActionHandlers() {
        return RuntimeActionExecutors.createRegistry(
                standardActionSupport(),
                healthActionSupport(),
                combatActionSupport(),
                STANDARD_COMBAT_OPTIONS,
                temporaryModifierActionSupport(),
                vampirismActionExtensions());
    }

    private StandardActionPacks.StandardActionSupport<SkillRuntimeContext> standardActionSupport() {
        return StandardActionSupports.<SkillRuntimeContext>standardBuilder()
                .definitionProvider(definitionProvider)
                .requirementEvaluator(requirementEvaluator::evaluateAll)
                .conditionEvaluator(conditionEvaluator::evaluateAll)
                .abilityActivator(this::activateAbility)
                .effectDurationResolver((effectDef, context) -> {
                    if (effectDef.duration <= 0f) {
                        return effectDef.duration;
                    }
                    float multiplier = Math.max(0f, VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(
                            VampireStatType.ABILITY_DURATION_MULTIPLIER.name(), 1f, context));
                    return effectDef.duration * multiplier;
                })
                .teleportTargetSanitizer((world, target, context) -> {
                    Vector3d safeTarget = WorldPositionHelper.findSafeGroundPosition(world, target);
                    return safeTarget != null ? safeTarget : target;
                })
                .build();
    }

    private StandardActionPacks.HealthActionSupport<SkillRuntimeContext> healthActionSupport() {
        return StandardActionSupports.<SkillRuntimeContext>healthBuilder()
                .statSupport(VampirismRuntimeStatSupport.RUNTIME)
                .defaultHealingMultiplierStatId(VampireStatType.HEALING_RECEIVED.name())
                .build();
    }

    private StandardActionPacks.CombatActionSupport<SkillRuntimeContext> combatActionSupport() {
        return StandardActionSupports.<SkillRuntimeContext>combatBuilder()
                .requirementEvaluator(requirementEvaluator::evaluateAll)
                .statResolver((statId, context) -> VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(statId, context))
                .onFinalBlow(this::handleFinalBlow)
                .build();
    }

    private StandardActionPacks.TemporaryModifierActionSupport<SkillRuntimeContext> temporaryModifierActionSupport() {
        return StandardActionSupports.<SkillRuntimeContext>temporaryModifierBuilder()
                .statSupport(VampirismRuntimeStatSupport.RUNTIME)
                .tracker(temporaryModifiers)
                .build();
    }

    private ActionHandlerPack<SkillRuntimeContext> vampirismActionExtensions() {
        return registry -> registry
                .register("startFeedChannel", ChannelActionHandlers::startFeedChannel)
                .register("startHealthToBloodChannel", ChannelActionHandlers::startHealthToBloodChannel)
                .register("modifyBlood", BloodActionHandler::modifyBlood)
                .register("applyTimedSpeedBoost", this::grantTemporaryModifierLegacySpeed)
                .register("applyControlEffect", this::deprecatedApplyControlEffect)
                .register("highlightEnemies", this::deprecatedHighlightEnemies);
    }

    private SkillActivationResult activateAbility(String abilityId, SkillRuntimeContext context) {
        return abilityActivator.apply(abilityId, context);
    }

    private void handleFinalBlow(SkillRuntimeContext context) {
        onFinalBlow.accept(context);
    }

    private boolean grantTemporaryModifierLegacySpeed(Map<String, Object> action, SkillRuntimeContext ctx) {
        Map<String, Object> rewritten = new java.util.LinkedHashMap<>(action);
        rewritten.put("type", "grantTemporaryModifier");
        rewritten.putIfAbsent("statId", "SPEED");
        Object boostStat = rewritten.remove("speedBoostStatId");
        if (boostStat != null) {
            rewritten.putIfAbsent("amountStatId", boostStat);
        }
        return execute(rewritten, ctx);
    }

    private boolean deprecatedApplyControlEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        LOGGER.atWarning().log("[SkillActionExecutor] 'applyControlEffect' is deprecated; migrate to 'applyEffect' with an effectId + targetingId: " + action);
        return false;
    }

    private boolean deprecatedHighlightEnemies(Map<String, Object> action, SkillRuntimeContext ctx) {
        LOGGER.atWarning().log("[SkillActionExecutor] 'highlightEnemies' is deprecated; migrate to 'applyEffect' with an effectId + targetingId: " + action);
        return false;
    }
}
