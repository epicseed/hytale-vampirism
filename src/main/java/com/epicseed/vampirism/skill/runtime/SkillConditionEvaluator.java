package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker;
import com.epicseed.epiccore.skill.runtime.conditions.ConditionHandlerRegistry;
import com.epicseed.epiccore.skill.runtime.conditions.RegistryBackedConditionEvaluator;
import com.epicseed.epiccore.skill.runtime.conditions.StandardConditionPacks;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.logger.HytaleLogger;

public final class SkillConditionEvaluator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final RegistryBackedConditionEvaluator<ModifierContext> MODIFIER_EVALUATOR =
            new RegistryBackedConditionEvaluator<>(createModifierHandlers());
    private static final RegistryBackedConditionEvaluator<SkillRuntimeContext> RUNTIME_EVALUATOR =
            new RegistryBackedConditionEvaluator<>(createRuntimeHandlers());

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

    private static ConditionHandlerRegistry<ModifierContext> createModifierHandlers() {
        return new ConditionHandlerRegistry<ModifierContext>()
                .install(StandardConditionPacks.generic(modifierSupport()))
                .register("bloodCompare", SkillConditionEvaluator::evaluateBloodCompare);
    }

    private static ConditionHandlerRegistry<SkillRuntimeContext> createRuntimeHandlers() {
        return new ConditionHandlerRegistry<SkillRuntimeContext>()
                .install(StandardConditionPacks.generic(runtimeSupport()))
                .register("bloodCompare", SkillConditionEvaluator::evaluateBloodCompare);
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
        Ability ability = VampirismProgressionDefinitionProvider.instance().getAbility(abilityId);
        if (ability == null) return true;
        return !AbilityCooldownTracker.isOnCooldown(uuid, abilityId);
    }

    private static String resolveEffectAssetId(String effectId) {
        EffectDef def = VampirismProgressionDefinitionProvider.instance().getEffect(effectId);
        return def != null && def.effectId != null && !def.effectId.isBlank()
                ? def.effectId
                : effectId;
    }

    private static StandardConditionPacks.StandardConditionSupport<ModifierContext> modifierSupport() {
        return new StandardConditionPacks.StandardConditionSupport<ModifierContext>() {
            @Override
            public com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> selfRef(ModifierContext context) {
                return context.ref();
            }

            @Override
            public com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> targetRef(ModifierContext context) {
                return null;
            }

            @Override
            public com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store(ModifierContext context) {
                return context.store();
            }

            @Override
            public boolean isStateActive(String stateId, ModifierContext context) {
                return SkillRuntimeStateResolver.isStateActive(stateId, context);
            }

            @Override
            public boolean isCooldownReady(String abilityId, ModifierContext context) {
                return SkillConditionEvaluator.isCooldownReady(context.uuid(), abilityId);
            }

            @Override
            public String resolveHytaleEffectId(String effectId, ModifierContext context) {
                return resolveEffectAssetId(effectId);
            }

            @Override
            public Float resolveStatValue(String statId, ModifierContext context) {
                return VampirismRuntimeStatSupport.MODIFIER.resolveStatValue(statId, context);
            }
        };
    }

    private static StandardConditionPacks.StandardConditionSupport<SkillRuntimeContext> runtimeSupport() {
        return new StandardConditionPacks.StandardConditionSupport<SkillRuntimeContext>() {
            @Override
            public com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> selfRef(SkillRuntimeContext context) {
                return context.ref();
            }

            @Override
            public com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> targetRef(SkillRuntimeContext context) {
                return context.targetRef();
            }

            @Override
            public com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store(SkillRuntimeContext context) {
                return context.store();
            }

            @Override
            public boolean isStateActive(String stateId, SkillRuntimeContext context) {
                return SkillRuntimeStateResolver.isStateActive(stateId, context);
            }

            @Override
            public boolean isCooldownReady(String abilityId, SkillRuntimeContext context) {
                return SkillConditionEvaluator.isCooldownReady(context.uuid(), abilityId);
            }

            @Override
            public String resolveHytaleEffectId(String effectId, SkillRuntimeContext context) {
                return resolveEffectAssetId(effectId);
            }

            @Override
            public Float resolveStatValue(String statId, SkillRuntimeContext context) {
                return VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(statId, context);
            }

            @Override
            public double resolveHealthPercentThreshold(Map<String, Object> condition,
                                                        SkillRuntimeContext context,
                                                        double declaredThreshold) {
                float thresholdOverride = VampirismRuntimeStatSupport.RUNTIME.resolveStatValue(
                        VampireStatType.ABILITY_EXECUTE_HEALTH_THRESHOLD.name(), -1f, context);
                String subject = condition.get("subject") instanceof String value ? value : "self";
                if ("target".equals(subject) && thresholdOverride >= 0f) {
                    return thresholdOverride;
                }
                return declaredThreshold;
            }
        };
    }
}
