package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Map;

import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.logger.HytaleLogger;

public final class BloodActionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private BloodActionHandler() {
    }

    public static boolean modifyBlood(Map<String, Object> action, SkillRuntimeContext ctx) {
        int amount;
        if (action.get("amount") instanceof Number n) {
            amount = normalizeBloodAmount(n.floatValue());
        } else if (action.get("percent") instanceof Number n) {
            amount = normalizeBloodPercent(n.floatValue(), VampireVitalitySystem.getMaxBlood(ctx.ref()));
        } else {
            LOGGER.atWarning().log("[SkillActionExecutor] modifyBlood missing amount/percent: " + action);
            return false;
        }
        String op = action.get("op") instanceof String s ? s : "add";
        switch (op) {
            case "add" -> {
                if (amount >= 0) {
                    VampireVitalitySystem.addBlood(ctx.ref(), amount);
                } else {
                    VampireVitalitySystem.spendBlood(ctx.ref(), -amount);
                }
            }
            case "set" -> {
                int current = VampireVitalitySystem.getBlood(ctx.ref());
                int delta = amount - current;
                if (delta >= 0) {
                    VampireVitalitySystem.addBlood(ctx.ref(), delta);
                } else {
                    VampireVitalitySystem.spendBlood(ctx.ref(), -delta);
                }
            }
            default -> {
                LOGGER.atWarning().log("[SkillActionExecutor] modifyBlood unsupported op: " + op);
                return false;
            }
        }
        return true;
    }

    private static int normalizeBloodAmount(float value) {
        float normalized = value;
        if (Math.abs(normalized) <= 1f) {
            normalized *= VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS;
        }
        return Math.round(normalized);
    }

    private static int normalizeBloodPercent(float value, int maxBlood) {
        float normalized = Math.abs(value) <= 1f ? value : value / 100f;
        return Math.round(normalized * Math.max(1, maxBlood));
    }
}
