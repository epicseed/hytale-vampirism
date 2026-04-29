package com.epicseed.vampirism.skill.runtime.actions;

import java.util.LinkedHashMap;
import java.util.Map;

import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.skill.runtime.TemporaryModifierTracker;
import com.hypixel.hytale.logger.HytaleLogger;

public final class ModifierActionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ModifierActionHandler() {
    }

    public static boolean grantTemporaryModifier(Map<String, Object> action, SkillRuntimeContext ctx) {
        if (ctx.uuid() == null) return false;

        String statId = action.get("statId") instanceof String s ? s : null;
        if (statId == null || statId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] grantTemporaryModifier missing statId: " + action);
            return false;
        }
        VampireStatType stat;
        try {
            stat = VampireStatType.valueOf(statId);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] grantTemporaryModifier unknown statId: " + statId);
            return false;
        }

        boolean isMultiplicative = action.containsKey("multiplier");
        TemporaryModifierTracker.Op op = isMultiplicative
                ? TemporaryModifierTracker.Op.MULTIPLICATIVE
                : TemporaryModifierTracker.Op.ADDITIVE;

        float amount = isMultiplicative
                ? resolveStatOrLiteral(action, "amountStatId", "multiplier", 1f, ctx)
                : resolveStatOrLiteral(action, "amountStatId", "amount", 0f, ctx);
        float duration = resolveStatOrLiteral(action, "durationStatId", "duration", 10f, ctx);

        if (!isMultiplicative && amount == 0f) {
            LOGGER.atWarning().log("[SkillActionExecutor] grantTemporaryModifier: resolved amount is 0 for " + statId);
            return false;
        }
        if (duration <= 0f) return false;

        TemporaryModifierTracker.Stacking stacking = resolveStacking(action);
        TemporaryModifierTracker.addBoost(ctx.uuid(), stat, amount, duration, stacking, op);
        LOGGER.atFine().log("[SkillActionExecutor] grantTemporaryModifier: " + statId + " "
                + (isMultiplicative ? "x" : "+") + amount + " for " + duration + "s -> " + ctx.uuid());
        return true;
    }

    public static boolean grantTemporaryModifierLegacySpeed(Map<String, Object> action, SkillRuntimeContext ctx) {
        Map<String, Object> rewritten = new LinkedHashMap<>(action);
        rewritten.putIfAbsent("statId", "SPEED");
        Object boostStat = rewritten.remove("speedBoostStatId");
        if (boostStat != null) rewritten.putIfAbsent("amountStatId", boostStat);
        return grantTemporaryModifier(rewritten, ctx);
    }

    private static TemporaryModifierTracker.Stacking resolveStacking(Map<String, Object> action) {
        Object raw = action.get("stacking");
        if (raw instanceof String s) {
            try {
                return TemporaryModifierTracker.Stacking.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                LOGGER.atWarning().log("[SkillActionExecutor] Unknown stacking policy: " + s);
            }
        }
        return TemporaryModifierTracker.Stacking.REPLACE;
    }

    private static float resolveStatOrLiteral(Map<String, Object> action,
                                              String statIdKey,
                                              String literalKey,
                                              float fallback,
                                              SkillRuntimeContext ctx) {
        Object statIdObj = action.get(statIdKey);
        if (statIdObj instanceof String statId && !statId.isBlank()) {
            try {
                VampireStatType stat = VampireStatType.valueOf(statId);
                return ModifierRegistry.get().compute(stat, fallback, ctx.modifierContext());
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("[SkillActionExecutor] Unknown stat for key '" + statIdKey + "': " + statId);
            }
        }
        Object literal = action.get(literalKey);
        return literal instanceof Number n ? n.floatValue() : fallback;
    }
}
