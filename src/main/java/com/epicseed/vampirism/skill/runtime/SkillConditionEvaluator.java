package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.runtime.CatalogBackedProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker;
import com.epicseed.epiccore.skill.runtime.conditions.ConditionHandlerPack;
import com.epicseed.epiccore.skill.runtime.conditions.RegistryBackedConditionEvaluator;
import com.epicseed.epiccore.skill.runtime.conditions.RuntimeConditionEvaluators;
import com.epicseed.epiccore.skill.runtime.conditions.StandardConditionPacks;
import com.epicseed.epiccore.skill.runtime.conditions.StandardConditionSupports;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.logger.HytaleLogger;

public final class SkillConditionEvaluator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final RegistryBackedConditionEvaluator<ModifierContext> MODIFIER_EVALUATOR =
            RuntimeConditionEvaluators.create(modifierSupport(), modifierExtensions());
    private static final RegistryBackedConditionEvaluator<SkillRuntimeContext> RUNTIME_EVALUATOR =
            RuntimeConditionEvaluators.create(runtimeSupport(), runtimeExtensions());

    private SkillConditionEvaluator() {}

    public static boolean evaluateAll(List<Map<String, Object>> conditions, ModifierContext ctx) {
        return MODIFIER_EVALUATOR.evaluateAll(conditions, ctx);
    }

    public static boolean evaluate(Map<String, Object> condition, ModifierContext ctx) {
        return MODIFIER_EVALUATOR.evaluate(condition, ctx);
    }

    public static boolean evaluateAll(List<Map<String, Object>> conditions, SkillRuntimeContext ctx) {
        return RUNTIME_EVALUATOR.evaluateAll(conditions, ctx);
    }

    public static boolean evaluate(Map<String, Object> condition, SkillRuntimeContext ctx) {
        return RUNTIME_EVALUATOR.evaluate(condition, ctx);
    }

    public static RegistryBackedConditionEvaluator<SkillRuntimeContext> runtimeEvaluator() {
        return RUNTIME_EVALUATOR;
    }

    private static ConditionHandlerPack<ModifierContext> modifierExtensions() {
        return registry -> registry.register("bloodCompare", SkillConditionEvaluator::evaluateBloodCompare);
    }

    private static ConditionHandlerPack<SkillRuntimeContext> runtimeExtensions() {
        return registry -> registry.register("bloodCompare", SkillConditionEvaluator::evaluateBloodCompare);
    }

    private static boolean evaluateBloodCompare(Map<String, Object> condition, SkillRuntimeContext ctx) {
        if (!(condition.get("value") instanceof Number n)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] bloodCompare missing numeric value: " + condition);
            return false;
        }
        String op = condition.get("op") instanceof String s ? s : "gte";
        int current = VampireVitalitySystem.getBlood(ctx.ref());
        return compareOp(op, current, ConditionEvaluationOperations.normalizeBloodValue(
                n, VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS));
    }

    private static boolean evaluateBloodCompare(Map<String, Object> condition, ModifierContext ctx) {
        if (!(condition.get("value") instanceof Number n)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] bloodCompare missing numeric value: " + condition);
            return false;
        }
        String op = condition.get("op") instanceof String s ? s : "gte";
        int current = VampireVitalitySystem.getBlood(ctx.ref());
        return compareOp(op, current, ConditionEvaluationOperations.normalizeBloodValue(
                n, VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS));
    }

    private static boolean compareOp(String op, float current, float value) {
        if (!ConditionEvaluationOperations.isCompareOperatorSupported(op)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported compare op: " + op);
            return false;
        }
        return ConditionEvaluationOperations.compare(op, current, value);
    }

    private static boolean isCooldownReady(UUID uuid, String abilityId) {
        if (uuid == null) return false;
        Ability ability = CatalogBackedProgressionDefinitionProvider.instance().getAbility(abilityId);
        if (ability == null) return true;
        return !AbilityCooldownTracker.isOnCooldown(uuid, abilityId);
    }

    private static String resolveEffectAssetId(String effectId) {
        EffectDef def = CatalogBackedProgressionDefinitionProvider.instance().getEffect(effectId);
        return def != null && def.effectId != null && !def.effectId.isBlank()
                ? def.effectId
                : effectId;
    }

    private static StandardConditionPacks.StandardConditionSupport<ModifierContext> modifierSupport() {
        return StandardConditionSupports.<ModifierContext>builder()
                .selfRef(ModifierContext::ref)
                .store(ModifierContext::store)
                .stateActive((stateId, context) -> SkillRuntimeStateResolver.isStateActive(stateId, context))
                .cooldownReady((abilityId, context) -> SkillConditionEvaluator.isCooldownReady(context.uuid(), abilityId))
                .effectIdResolver((effectId, context) -> resolveEffectAssetId(effectId))
                .statValueResolver((statId, context) -> VampirismRuntimeStatSupport.MODIFIER.resolveStatValue(statId, context))
                .build();
    }

    private static StandardConditionPacks.StandardConditionSupport<SkillRuntimeContext> runtimeSupport() {
        return StandardConditionSupports.<SkillRuntimeContext>builder()
                .selfRef(SkillRuntimeContext::ref)
                .targetRef(SkillRuntimeContext::targetRef)
                .store(SkillRuntimeContext::store)
                .stateActive((stateId, context) -> SkillRuntimeStateResolver.isStateActive(stateId, context))
                .cooldownReady((abilityId, context) -> SkillConditionEvaluator.isCooldownReady(context.uuid(), abilityId))
                .effectIdResolver((effectId, context) -> resolveEffectAssetId(effectId))
                .statValueResolver((statId, context) -> VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(statId, context))
                .healthThresholdResolver((condition, context, declaredThreshold) -> {
                    float thresholdOverride = VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(
                            VampireStatType.ABILITY_EXECUTE_HEALTH_THRESHOLD.name(), -1f, context);
                    String subject = condition.get("subject") instanceof String value ? value : "self";
                    if ("target".equals(subject) && thresholdOverride >= 0f) {
                        return thresholdOverride;
                    }
                    return declaredThreshold;
                })
                .build();
    }
}
