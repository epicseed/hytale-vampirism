package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.vampirism.skill.runtime.SkillRuntimeStateResolver;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
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
    private final ProgressionDefinitionProvider definitionProvider;
    private final SkillRuntimeStateResolver<ModifierContext, SkillRuntimeContext> runtimeStateResolver;
    private final RegistryBackedConditionEvaluator<ModifierContext> modifierEvaluator;
    private final RegistryBackedConditionEvaluator<SkillRuntimeContext> runtimeEvaluator;

    public SkillConditionEvaluator(@Nonnull ProgressionDefinitionProvider definitionProvider,
                                   @Nonnull SkillRuntimeStateResolver<ModifierContext, SkillRuntimeContext> runtimeStateResolver) {
        this.definitionProvider = definitionProvider;
        this.runtimeStateResolver = runtimeStateResolver;
        this.modifierEvaluator = RuntimeConditionEvaluators.create(modifierSupport(), modifierExtensions());
        this.runtimeEvaluator = RuntimeConditionEvaluators.create(runtimeSupport(), runtimeExtensions());
    }

    public boolean evaluateAll(List<Map<String, Object>> conditions, ModifierContext ctx) {
        return modifierEvaluator.evaluateAll(conditions, ctx);
    }

    public boolean evaluate(Map<String, Object> condition, ModifierContext ctx) {
        return modifierEvaluator.evaluate(condition, ctx);
    }

    public boolean evaluateAll(List<Map<String, Object>> conditions, SkillRuntimeContext ctx) {
        return runtimeEvaluator.evaluateAll(conditions, ctx);
    }

    public boolean evaluate(Map<String, Object> condition, SkillRuntimeContext ctx) {
        return runtimeEvaluator.evaluate(condition, ctx);
    }

    private ConditionHandlerPack<ModifierContext> modifierExtensions() {
        return registry -> registry.register("bloodCompare", this::evaluateBloodCompare);
    }

    private ConditionHandlerPack<SkillRuntimeContext> runtimeExtensions() {
        return registry -> registry.register("bloodCompare", this::evaluateBloodCompare);
    }

    private boolean evaluateBloodCompare(Map<String, Object> condition, SkillRuntimeContext ctx) {
        if (!(condition.get("value") instanceof Number n)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] bloodCompare missing numeric value: " + condition);
            return false;
        }
        String op = condition.get("op") instanceof String s ? s : "gte";
        int current = VampireVitalitySystem.getBlood(ctx.ref());
        return compareOp(op, current, ConditionEvaluationOperations.normalizeBloodValue(
                n, VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS));
    }

    private boolean evaluateBloodCompare(Map<String, Object> condition, ModifierContext ctx) {
        if (!(condition.get("value") instanceof Number n)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] bloodCompare missing numeric value: " + condition);
            return false;
        }
        String op = condition.get("op") instanceof String s ? s : "gte";
        int current = VampireVitalitySystem.getBlood(ctx.ref());
        return compareOp(op, current, ConditionEvaluationOperations.normalizeBloodValue(
                n, VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS));
    }

    private boolean compareOp(String op, float current, float value) {
        if (!ConditionEvaluationOperations.isCompareOperatorSupported(op)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported compare op: " + op);
            return false;
        }
        return ConditionEvaluationOperations.compare(op, current, value);
    }

    private boolean isCooldownReady(UUID uuid, String abilityId) {
        if (uuid == null) return false;
        Ability ability = definitionProvider.getAbility(abilityId);
        if (ability == null) return true;
        return !AbilityCooldownTracker.isOnCooldown(uuid, abilityId);
    }

    private String resolveEffectAssetId(String effectId) {
        EffectDef def = definitionProvider.getEffect(effectId);
        return def != null && def.effectId != null && !def.effectId.isBlank()
                ? def.effectId
                : effectId;
    }

    private StandardConditionPacks.StandardConditionSupport<ModifierContext> modifierSupport() {
        return StandardConditionSupports.<ModifierContext>builder()
                .selfRef(ModifierContext::ref)
                .store(ModifierContext::store)
                .stateActive(runtimeStateResolver::isModifierStateActive)
                .cooldownReady((abilityId, context) -> isCooldownReady(context.uuid(), abilityId))
                .effectIdResolver((effectId, context) -> this.resolveEffectAssetId(effectId))
                .statValueResolver((statId, context) -> VampirismRuntimeStatSupport.MODIFIER.resolveStatValue(statId, context))
                .build();
    }

    private StandardConditionPacks.StandardConditionSupport<SkillRuntimeContext> runtimeSupport() {
        return StandardConditionSupports.<SkillRuntimeContext>builder()
                .selfRef(SkillRuntimeContext::ref)
                .targetRef(SkillRuntimeContext::targetRef)
                .store(SkillRuntimeContext::store)
                .stateActive(runtimeStateResolver::isRuntimeStateActive)
                .cooldownReady((abilityId, context) -> isCooldownReady(context.uuid(), abilityId))
                .effectIdResolver((effectId, context) -> this.resolveEffectAssetId(effectId))
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
