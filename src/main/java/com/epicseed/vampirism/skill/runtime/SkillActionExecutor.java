package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.runtime.actions.ActionHandlerPack;
import com.epicseed.epiccore.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.epiccore.skill.runtime.actions.RuntimeActionExecutors;
import com.epicseed.epiccore.skill.runtime.actions.StandardActionPacks;
import com.epicseed.epiccore.skill.runtime.actions.StandardActionSupports;
import com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker;
import com.epicseed.vampirism.skill.runtime.actions.BloodActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ChannelActionHandlers;
import com.epicseed.vampirism.skill.runtime.actions.ModifierActionHandler;
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
    private static final TemporaryModifierTracker<StatType> TEMPORARY_MODIFIERS =
            com.epicseed.vampirism.skill.runtime.TemporaryModifierTracker.sharedTracker();
    private static volatile Consumer<SkillRuntimeContext> onFinalBlow = ctx -> {};

    private static final com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor<SkillRuntimeContext> EXECUTOR =
            RuntimeActionExecutors.create(
                    standardActionSupport(),
                    SkillConditionEvaluator::evaluateAll,
                    healthActionSupport(),
                    combatActionSupport(),
                    STANDARD_COMBAT_OPTIONS,
                    temporaryModifierActionSupport(),
                    vampirismActionExtensions());

    private SkillActionExecutor() {
    }

    public static void init(Consumer<SkillRuntimeContext> onFinalBlow) {
        SkillActionExecutor.onFinalBlow = onFinalBlow != null ? onFinalBlow : ctx -> {};
    }

    public static boolean executeAll(List<Map<String, Object>> actions, SkillRuntimeContext ctx) {
        return EXECUTOR.executeAll(actions, ctx);
    }

    public static boolean execute(Map<String, Object> action, SkillRuntimeContext ctx) {
        return EXECUTOR.execute(action, ctx);
    }

    public static com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor<SkillRuntimeContext> sharedExecutor() {
        return EXECUTOR;
    }

    static ActionHandlerRegistry<SkillRuntimeContext> createActionHandlers() {
        return RuntimeActionExecutors.createRegistry(
                standardActionSupport(),
                healthActionSupport(),
                combatActionSupport(),
                STANDARD_COMBAT_OPTIONS,
                temporaryModifierActionSupport(),
                vampirismActionExtensions());
    }

    private static StandardActionPacks.StandardActionSupport<SkillRuntimeContext> standardActionSupport() {
        return StandardActionSupports.<SkillRuntimeContext>standardBuilder()
                .definitionProvider(com.epicseed.epiccore.skill.runtime.CatalogBackedProgressionDefinitionProvider.instance())
                .requirementEvaluator(SkillRequirementEvaluator::evaluateAll)
                .conditionEvaluator(SkillConditionEvaluator::evaluateAll)
                .abilityActivator(AbilityService::activateFromAction)
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

    private static StandardActionPacks.HealthActionSupport<SkillRuntimeContext> healthActionSupport() {
        return StandardActionSupports.<SkillRuntimeContext>healthBuilder()
                .statSupport(VampirismRuntimeStatSupport.RUNTIME)
                .defaultHealingMultiplierStatId(VampireStatType.HEALING_RECEIVED.name())
                .build();
    }

    private static StandardActionPacks.CombatActionSupport<SkillRuntimeContext> combatActionSupport() {
        return StandardActionSupports.<SkillRuntimeContext>combatBuilder()
                .requirementEvaluator(SkillRequirementEvaluator::evaluateAll)
                .statResolver((statId, context) -> VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(statId, context))
                .onFinalBlow(context -> onFinalBlow.accept(context))
                .build();
    }

    private static StandardActionPacks.TemporaryModifierActionSupport<SkillRuntimeContext> temporaryModifierActionSupport() {
        return StandardActionSupports.<SkillRuntimeContext>temporaryModifierBuilder()
                .statSupport(VampirismRuntimeStatSupport.RUNTIME)
                .tracker(TEMPORARY_MODIFIERS)
                .build();
    }

    private static ActionHandlerPack<SkillRuntimeContext> vampirismActionExtensions() {
        return registry -> registry
                .register("startFeedChannel", ChannelActionHandlers::startFeedChannel)
                .register("startHealthToBloodChannel", ChannelActionHandlers::startHealthToBloodChannel)
                .register("modifyBlood", BloodActionHandler::modifyBlood)
                .register("applyTimedSpeedBoost", ModifierActionHandler::grantTemporaryModifierLegacySpeed)
                .register("applyControlEffect", SkillActionExecutor::deprecatedApplyControlEffect)
                .register("highlightEnemies", SkillActionExecutor::deprecatedHighlightEnemies);
    }

    private static boolean deprecatedApplyControlEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        LOGGER.atWarning().log("[SkillActionExecutor] 'applyControlEffect' is deprecated; migrate to 'applyEffect' with an effectId + targetingId: " + action);
        return false;
    }

    private static boolean deprecatedHighlightEnemies(Map<String, Object> action, SkillRuntimeContext ctx) {
        LOGGER.atWarning().log("[SkillActionExecutor] 'highlightEnemies' is deprecated; migrate to 'applyEffect' with an effectId + targetingId: " + action);
        return false;
    }
}
