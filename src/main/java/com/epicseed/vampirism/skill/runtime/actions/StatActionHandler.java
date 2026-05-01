package com.epicseed.vampirism.skill.runtime.actions;
import com.epicseed.vampirism.modifier.ModifierContext;

import java.util.Map;

import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

public final class StatActionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private StatActionHandler() {
    }

    public static boolean healSelf(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object statIdObj = action.get("statId");
        if (!(statIdObj instanceof String statId) || statId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] healSelf missing statId: " + action);
            return false;
        }

        VampireStatType statType;
        try {
            statType = VampireStatType.valueOf(statId);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] healSelf unknown statId: " + statId);
            return false;
        }

        float healAmount = ModifierContext.REGISTRY.compute(statType, 0f, ctx.modifierContext());
        float healingReceived = Math.max(0f, ModifierContext.REGISTRY.compute(
                VampireStatType.HEALING_RECEIVED, 1f, ctx.modifierContext()));
        healAmount *= healingReceived;
        if (healAmount <= 0f) return false;

        EntityStatMap stats = (EntityStatMap) ctx.store().getComponent(ctx.ref(), EntityStatMap.getComponentType());
        if (stats == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] healSelf: no EntityStatMap for " + ctx.uuid());
            return false;
        }

        stats.addStatValue(DefaultEntityStatTypes.getHealth(), healAmount);
        LOGGER.atFine().log("[SkillActionExecutor] healSelf: +" + healAmount + " HP for " + ctx.uuid());
        return true;
    }

    public static boolean modifyStat(Map<String, Object> action, SkillRuntimeContext ctx) {
        String statId = action.get("statId") instanceof String s ? s : null;
        if (statId == null || statId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] modifyStat missing statId: " + action);
            return false;
        }
        String op = action.get("op") instanceof String s ? s : "add";
        float amount = action.get("amount") instanceof Number n ? n.floatValue() : 0f;

        if ("HEALTH".equalsIgnoreCase(statId)) {
            EntityStatMap stats = (EntityStatMap) ctx.store().getComponent(ctx.ref(), EntityStatMap.getComponentType());
            if (stats == null) return false;
            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
            if (health == null) return false;
            switch (op) {
                case "add" -> stats.addStatValue(DefaultEntityStatTypes.getHealth(), amount);
                case "set" -> stats.addStatValue(DefaultEntityStatTypes.getHealth(), amount - health.get());
                case "mul" -> stats.addStatValue(DefaultEntityStatTypes.getHealth(), health.get() * (amount - 1f));
                default -> {
                    LOGGER.atWarning().log("[SkillActionExecutor] modifyStat unsupported op: " + op);
                    return false;
                }
            }
            return true;
        }

        LOGGER.atWarning().log("[SkillActionExecutor] modifyStat: statId '" + statId
                + "' is a derived VampireStatType; use grantTemporaryModifier instead.");
        return false;
    }
}
