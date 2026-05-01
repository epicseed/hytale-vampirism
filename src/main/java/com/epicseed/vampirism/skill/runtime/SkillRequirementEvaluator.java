package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.List;
import java.util.Map;

public final class SkillRequirementEvaluator {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private SkillRequirementEvaluator() {}

    public static boolean evaluateAll(List<Map<String, Object>> requirements, SkillRuntimeContext ctx) {
        if (requirements == null || requirements.isEmpty()) return true;
        for (Map<String, Object> requirement : requirements) {
            if (!evaluate(requirement, ctx)) return false;
        }
        return true;
    }

    public static boolean evaluate(Map<String, Object> requirement, SkillRuntimeContext ctx) {
        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveRequirement(requirement);
        Object type = resolved.get("type");
        if (!(type instanceof String typeId) || typeId.isBlank()) {
            LOGGER.atWarning().log("[SkillRequirementEvaluator] Requirement without type: " + resolved);
            return false;
        }

        return switch (typeId) {
            case "condition" -> evaluateConditionRequirement(resolved, ctx);
            case "abilityUnlocked" -> hasUnlockedAbility(ctx, stringValue(resolved, "abilityId"));
            case "passiveUnlocked" -> hasUnlockedPassive(ctx, stringValue(resolved, "passiveId"));
            default -> {
                LOGGER.atWarning().log("[SkillRequirementEvaluator] Unsupported requirement type: " + typeId);
                yield false;
            }
        };
    }

    private static boolean evaluateConditionRequirement(Map<String, Object> requirement, SkillRuntimeContext ctx) {
        if (requirement.get("condition") instanceof Map<?, ?> inlineCondition) {
            @SuppressWarnings("unchecked")
            Map<String, Object> condition = (Map<String, Object>) inlineCondition;
            return SkillConditionEvaluator.evaluate(condition, ctx);
        }

        String conditionId = stringValue(requirement, "conditionId");
        if (conditionId == null) {
            LOGGER.atWarning().log("[SkillRequirementEvaluator] condition requirement missing conditionId: " + requirement);
            return false;
        }
        return SkillConditionEvaluator.evaluate(Map.of("conditionId", conditionId), ctx);
    }

    private static boolean hasUnlockedAbility(SkillRuntimeContext ctx, String abilityId) {
        if (ctx.uuid() == null || abilityId == null || abilityId.isBlank()) return false;
        for (String unlockedSkillId : PlayerSkillRegistry.get().getUnlockedSkills(ctx.uuid())) {
            Skill skill = Vampirism.getInstance().GetSkillRegistry().GetSkill(unlockedSkillId);
            if (skill != null && abilityId.equals(skill.abilityId)) return true;
        }
        return false;
    }

    private static boolean hasUnlockedPassive(SkillRuntimeContext ctx, String passiveId) {
        if (ctx.uuid() == null || passiveId == null || passiveId.isBlank()) return false;
        for (String unlockedSkillId : PlayerSkillRegistry.get().getUnlockedSkills(ctx.uuid())) {
            Skill skill = Vampirism.getInstance().GetSkillRegistry().GetSkill(unlockedSkillId);
            if (skill != null && passiveId.equals(skill.passiveId)) return true;
        }
        return false;
    }

    private static String stringValue(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        return raw instanceof String string ? string : null;
    }
}
