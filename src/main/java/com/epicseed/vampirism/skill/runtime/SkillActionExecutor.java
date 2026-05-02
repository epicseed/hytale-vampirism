package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.epiccore.skill.runtime.actions.StandardActionPacks;
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
            new com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor<>(
                    createActionHandlers(),
                    SkillConditionEvaluator::evaluateAll);

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

    private static ActionHandlerRegistry<SkillRuntimeContext> createActionHandlers() {
        return new ActionHandlerRegistry<SkillRuntimeContext>()
                .install(StandardActionPacks.safeGeneric(new StandardActionPacks.StandardActionSupport<SkillRuntimeContext>() {
                    @Override
                    public VampirismProgressionDefinitionProvider definitionProvider() {
                        return VampirismProgressionDefinitionProvider.instance();
                    }

                    @Override
                    public com.epicseed.epiccore.skill.runtime.AbilityRequirementEvaluator<SkillRuntimeContext> requirementEvaluator() {
                        return SkillRequirementEvaluator::evaluateAll;
                    }

                    @Override
                    public com.epicseed.epiccore.skill.runtime.actions.ActionConditionEvaluator<SkillRuntimeContext> conditionEvaluator() {
                        return SkillConditionEvaluator::evaluateAll;
                    }

                    @Override
                    public com.epicseed.epiccore.skill.runtime.SkillActivationResult activateAbility(String abilityId, SkillRuntimeContext context) {
                        return AbilityService.activateFromAction(abilityId, context);
                    }

                    @Override
                    public float resolveEffectDuration(EffectDef effectDef, SkillRuntimeContext context) {
                        if (effectDef.duration <= 0f) {
                            return effectDef.duration;
                        }
                        float multiplier = Math.max(0f, VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(
                                VampireStatType.ABILITY_DURATION_MULTIPLIER.name(), 1f, context));
                        return effectDef.duration * multiplier;
                    }

                    @Override
                    public Vector3d sanitizeTeleportTarget(World world, Vector3d target, SkillRuntimeContext context) {
                        Vector3d safeTarget = WorldPositionHelper.findSafeGroundPosition(world, target);
                        return safeTarget != null ? safeTarget : target;
                    }
                }))
                .install(StandardActionPacks.health(new StandardActionPacks.HealthActionSupport<SkillRuntimeContext>() {
                    @Override
                    public com.epicseed.epiccore.skill.runtime.stats.RuntimeStatSupport<SkillRuntimeContext> statSupport() {
                        return VampirismRuntimeStatSupport.RUNTIME;
                    }

                    @Override
                    public String defaultHealingMultiplierStatId() {
                        return VampireStatType.HEALING_RECEIVED.name();
                    }
                }))
                .install(StandardActionPacks.combat(new StandardActionPacks.CombatActionSupport<SkillRuntimeContext>() {
                    @Override
                    public com.epicseed.epiccore.skill.runtime.AbilityRequirementEvaluator<SkillRuntimeContext> requirementEvaluator() {
                        return SkillRequirementEvaluator::evaluateAll;
                    }

                    @Override
                    public Float resolveStat(String statId, SkillRuntimeContext context) {
                        return VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(statId, context);
                    }

                    @Override
                    public void onFinalBlow(SkillRuntimeContext context) {
                        onFinalBlow.accept(context);
                    }
                }, STANDARD_COMBAT_OPTIONS))
                .install(StandardActionPacks.temporaryModifiers(new StandardActionPacks.TemporaryModifierActionSupport<SkillRuntimeContext>() {
                    @Override
                    public com.epicseed.epiccore.skill.runtime.stats.RuntimeStatSupport<SkillRuntimeContext> statSupport() {
                        return VampirismRuntimeStatSupport.RUNTIME;
                    }

                    @Override
                    public TemporaryModifierTracker<StatType> tracker() {
                        return TEMPORARY_MODIFIERS;
                    }
                }))
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
