package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.Map;

public final class SkillConditionEvaluator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private SkillConditionEvaluator() {}

    public static boolean evaluateAll(List<Map<String, Object>> conditions, ModifierContext ctx) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (Map<String, Object> condition : conditions) {
            if (!evaluate(condition, ctx)) return false;
        }
        return true;
    }

    public static boolean evaluate(Map<String, Object> condition, ModifierContext ctx) {
        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveCondition(condition);
        Object type = resolved.get("type");
        if (!(type instanceof String typeId) || typeId.isBlank()) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] Condition without type: " + resolved);
            return false;
        }

        return switch (typeId) {
            case "state" -> evaluateState(resolved, ctx);
            case "effectActive" -> evaluateEffectActive(resolved, ctx);
            case "equipmentSet" -> evaluateEquipmentSet(resolved, ctx);
            case "equippedItem" -> evaluateEquippedItem(resolved, ctx);
            case "companionPresent" -> evaluateCompanionPresent(resolved, ctx);
            case "bloodCompare" -> evaluateBloodCompare(resolved, ctx);
            case "statCompare" -> evaluateStatCompare(resolved, ctx);
            case "cooldownReady" -> evaluateCooldownReady(resolved, ctx);
            default -> {
                LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported condition type: " + typeId);
                yield false;
            }
        };
    }

    public static boolean evaluateAll(List<Map<String, Object>> conditions, SkillRuntimeContext ctx) {
        if (conditions == null || conditions.isEmpty()) return true;
        for (Map<String, Object> condition : conditions) {
            if (!evaluate(condition, ctx)) return false;
        }
        return true;
    }

    public static boolean evaluate(Map<String, Object> condition, SkillRuntimeContext ctx) {
        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveCondition(condition);
        Object type = resolved.get("type");
        if (!(type instanceof String typeId) || typeId.isBlank()) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] Condition without type: " + resolved);
            return false;
        }

        return switch (typeId) {
            case "state" -> evaluateState(resolved, ctx);
            case "healthPercent" -> evaluateHealthPercent(resolved, ctx);
            case "effectActive" -> evaluateEffectActive(resolved, ctx);
            case "equipmentSet" -> evaluateEquipmentSet(resolved, ctx);
            case "equippedItem" -> evaluateEquippedItem(resolved, ctx);
            case "companionPresent" -> evaluateCompanionPresent(resolved, ctx);
            case "bloodCompare" -> evaluateBloodCompare(resolved, ctx);
            case "statCompare" -> evaluateStatCompare(resolved, ctx);
            case "cooldownReady" -> evaluateCooldownReady(resolved, ctx);
            default -> {
                LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported runtime condition type: " + typeId);
                yield false;
            }
        };
    }

    private static boolean evaluateState(Map<String, Object> condition, SkillRuntimeContext ctx) {
        String state = stateId(condition);
        if (state == null) {
            return false;
        }
        boolean active = SkillRuntimeStateResolver.isStateActive(state, ctx);
        return evaluateStateOperator(stateOperator(condition), active);
    }

    private static boolean evaluateState(Map<String, Object> condition, ModifierContext ctx) {
        String state = stateId(condition);
        if (state == null) {
            return false;
        }
        boolean active = SkillRuntimeStateResolver.isStateActive(state, ctx);
        return evaluateStateOperator(stateOperator(condition), active);
    }

    private static boolean evaluateEffectActive(Map<String, Object> condition, SkillRuntimeContext ctx) {
        int effectIndex = resolveEffectIndex(condition);
        if (effectIndex < 0) return false;

        EffectControllerComponent ec = (EffectControllerComponent) ctx.store().getComponent(
                ctx.ref(), EffectControllerComponent.getComponentType());
        return ec != null && ec.hasEffect(effectIndex);
    }

    private static boolean evaluateEffectActive(Map<String, Object> condition, ModifierContext ctx) {
        int effectIndex = resolveEffectIndex(condition);
        if (effectIndex < 0) return false;

        Store<EntityStore> store = ctx.store();
        if (store == null) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] effectActive requires store-backed ModifierContext: " + condition);
            return false;
        }

        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                ctx.ref(), EffectControllerComponent.getComponentType());
        return ec != null && ec.hasEffect(effectIndex);
    }

    private static boolean evaluateEquipmentSet(Map<String, Object> condition, SkillRuntimeContext ctx) {
        String setId = condition.get("setId") instanceof String value ? value : null;
        String operator = condition.get("operator") instanceof String value ? value : "equipped";
        if (!"equipped".equals(operator)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported equipmentSet operator: " + operator);
            return false;
        }
        return SkillRuntimeQueries.isEquipmentSetEquipped(ctx.ref(), ctx.store(), setId);
    }

    private static boolean evaluateEquipmentSet(Map<String, Object> condition, ModifierContext ctx) {
        Store<EntityStore> store = ctx.store();
        if (store == null) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] equipmentSet requires store-backed ModifierContext: " + condition);
            return false;
        }
        String setId = condition.get("setId") instanceof String value ? value : null;
        String operator = condition.get("operator") instanceof String value ? value : "equipped";
        if (!"equipped".equals(operator)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported equipmentSet operator: " + operator);
            return false;
        }
        return SkillRuntimeQueries.isEquipmentSetEquipped(ctx.ref(), store, setId);
    }

    private static boolean evaluateEquippedItem(Map<String, Object> condition, SkillRuntimeContext ctx) {
        String itemId = condition.get("itemId") instanceof String value ? value : null;
        return SkillRuntimeQueries.isHeldItemEquipped(ctx.ref(), ctx.store(), itemId);
    }

    private static boolean evaluateEquippedItem(Map<String, Object> condition, ModifierContext ctx) {
        Store<EntityStore> store = ctx.store();
        if (store == null) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] equippedItem requires store-backed ModifierContext: " + condition);
            return false;
        }
        String itemId = condition.get("itemId") instanceof String value ? value : null;
        return SkillRuntimeQueries.isHeldItemEquipped(ctx.ref(), store, itemId);
    }

    private static boolean evaluateCompanionPresent(Map<String, Object> condition, SkillRuntimeContext ctx) {
        Number radius = condition.get("radius") instanceof Number value ? value : null;
        return SkillRuntimeQueries.hasNearbyOwnedDeployable(ctx.ref(), ctx.store(), radius);
    }

    private static boolean evaluateCompanionPresent(Map<String, Object> condition, ModifierContext ctx) {
        Store<EntityStore> store = ctx.store();
        if (store == null) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] companionPresent requires store-backed ModifierContext: " + condition);
            return false;
        }
        Number radius = condition.get("radius") instanceof Number value ? value : null;
        return SkillRuntimeQueries.hasNearbyOwnedDeployable(ctx.ref(), store, radius);
    }

    private static int resolveEffectIndex(Map<String, Object> condition) {
        Object effectIdValue = condition.get("effectId");
        if (!(effectIdValue instanceof String effectId) || effectId.isBlank()) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] effectActive missing effectId: " + condition);
            return -1;
        }

        EffectDef def = Vampirism.getInstance().GetEffectDefRegistry().Get(effectId);
        String hytaleId = def != null ? def.effectId : effectId;

        int idx = EntityEffect.getAssetMap().getIndex(hytaleId);
        if (idx < 0) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] effectActive: Hytale effect not found: " + hytaleId);
        }
        return idx;
    }

    private static boolean evaluateHealthPercent(Map<String, Object> condition, SkillRuntimeContext ctx) {
        String subject = condition.get("subject") instanceof String value ? value : "self";
        Object value = condition.get("value");
        if (!(value instanceof Number threshold)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] healthPercent missing numeric value: " + condition);
            return false;
        }

        var targetRef = "target".equals(subject) ? ctx.targetRef() : ctx.ref();
        if (targetRef == null) return false;

        EntityStatMap stats = (EntityStatMap) ctx.store().getComponent(targetRef, EntityStatMap.getComponentType());
        if (stats == null) return false;

        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.getMax() <= 0f) return false;

        double current = health.get() / health.getMax();
        String operator = condition.get("operator") instanceof String op ? op : "<=";
        double target = threshold.doubleValue();
        float thresholdOverride = ModifierRegistry.get().compute(
                VampireStatType.ABILITY_EXECUTE_HEALTH_THRESHOLD, -1f, ctx.modifierContext());
        if ("target".equals(subject) && thresholdOverride >= 0f) {
            target = thresholdOverride;
        }

        return switch (operator) {
            case "<" -> current < target;
            case "<=" -> current <= target;
            case ">" -> current > target;
            case ">=" -> current >= target;
            case "==", "=" -> Math.abs(current - target) < 0.0001d;
            default -> {
                LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported healthPercent operator: " + operator);
                yield false;
            }
        };
    }

    // -------------------------------------------------------------------------
    // New conditions: bloodCompare / statCompare / cooldownReady
    // -------------------------------------------------------------------------

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

    private static boolean evaluateStatCompare(Map<String, Object> condition, SkillRuntimeContext ctx) {
        return evaluateStatCompareImpl(condition, ctx.modifierContext());
    }

    private static boolean evaluateStatCompare(Map<String, Object> condition, ModifierContext ctx) {
        return evaluateStatCompareImpl(condition, ctx);
    }

    private static boolean evaluateStatCompareImpl(Map<String, Object> condition, ModifierContext ctx) {
        String statId = condition.get("statId") instanceof String s ? s : null;
        if (statId == null || statId.isBlank()) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] statCompare missing statId: " + condition);
            return false;
        }
        VampireStatType stat;
        try {
            stat = VampireStatType.valueOf(statId);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] statCompare unknown statId: " + statId);
            return false;
        }
        if (!(condition.get("value") instanceof Number n)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] statCompare missing numeric value: " + condition);
            return false;
        }
        String op = condition.get("op") instanceof String s ? s : "gte";
        float current = ModifierRegistry.get().compute(stat, 0f, ctx);
        return compareOp(op, current, n.floatValue());
    }

    private static boolean evaluateCooldownReady(Map<String, Object> condition, SkillRuntimeContext ctx) {
        return evaluateCooldownReadyImpl(condition, ctx.uuid());
    }

    private static boolean evaluateCooldownReady(Map<String, Object> condition, ModifierContext ctx) {
        return evaluateCooldownReadyImpl(condition, ctx.uuid());
    }

    private static boolean evaluateCooldownReadyImpl(Map<String, Object> condition, java.util.UUID uuid) {
        if (uuid == null) return false;
        String abilityId = condition.get("abilityId") instanceof String s ? s : null;
        if (abilityId == null || abilityId.isBlank()) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] cooldownReady missing abilityId: " + condition);
            return false;
        }
        // Avoid noisy warnings when the ability is unknown — treat as ready.
        Ability ability = Vampirism.getInstance().GetAbilityRegistry().Get(abilityId);
        if (ability == null) return true;
        return !AbilityCooldownTracker.isOnCooldown(uuid, abilityId);
    }

    private static boolean compareOp(String op, float current, float value) {
        if (!ConditionEvaluationOperations.isCompareOperatorSupported(op)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported compare op: " + op);
            return false;
        }
        return ConditionEvaluationOperations.compare(op, current, value);
    }

    private static boolean evaluateStateOperator(String operator, boolean active) {
        if (!ConditionEvaluationOperations.isStateOperatorSupported(operator)) {
            LOGGER.atWarning().log("[SkillConditionEvaluator] Unsupported state operator: " + operator);
            return false;
        }
        return ConditionEvaluationOperations.evaluateStateOperator(operator, active);
    }

    private static String stateId(Map<String, Object> condition) {
        Object stateId = condition.get("stateId");
        if (stateId instanceof String state) {
            return state;
        }
        LOGGER.atWarning().log("[SkillConditionEvaluator] state condition missing stateId: " + condition);
        return null;
    }

    private static String stateOperator(Map<String, Object> condition) {
        return condition.get("operator") instanceof String value ? value : "isTrue";
    }
}
