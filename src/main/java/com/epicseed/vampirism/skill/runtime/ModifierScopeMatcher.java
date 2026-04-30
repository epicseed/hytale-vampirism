package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.epiccore.skill.model.InlineModifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public final class ModifierScopeMatcher {

    private ModifierScopeMatcher() {}

    public static boolean applies(@Nonnull InlineModifier mod, @Nonnull ModifierContext ctx) {
        return matchesTarget(mod.target, ctx) && SkillConditionEvaluator.evaluateAll(mod.conditions, ctx);
    }

    public static boolean matchesTarget(@Nullable Map<String, Object> target, @Nonnull ModifierContext ctx) {
        if (target == null || target.isEmpty()) return true;

        String type = asString(target.get("type"));
        if (type == null || type.isBlank()) {
            if (target.containsKey("abilityId")) return matchesId(asString(target.get("abilityId")), ctx.abilityId());
            if (target.containsKey("effectId")) return matchesId(asString(target.get("effectId")), ctx.effectId());
            if (target.containsKey("passiveId")) return matchesId(asString(target.get("passiveId")), ctx.passiveId());
            if (target.containsKey("skillId")) return matchesId(asString(target.get("skillId")), ctx.skillId());
            return true;
        }

        return switch (type) {
            case "ability" -> matchesId(resolveTargetId(target, "abilityId"), ctx.abilityId());
            case "effect" -> matchesId(resolveTargetId(target, "effectId"), ctx.effectId());
            case "passive" -> matchesId(resolveTargetId(target, "passiveId"), ctx.passiveId());
            case "skill" -> matchesId(resolveTargetId(target, "skillId"), ctx.skillId());
            default -> true;
        };
    }

    @Nullable
    private static String resolveTargetId(@Nonnull Map<String, Object> target, @Nonnull String scopedKey) {
        String scoped = asString(target.get(scopedKey));
        if (scoped != null && !scoped.isBlank()) return scoped;
        return asString(target.get("id"));
    }

    private static boolean matchesId(@Nullable String expected, @Nullable String actual) {
        return expected == null || expected.isBlank() || expected.equals(actual);
    }

    @Nullable
    private static String asString(@Nullable Object value) {
        if (!(value instanceof String text)) return null;
        return text.isBlank() ? null : text;
    }
}
