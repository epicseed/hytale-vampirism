package com.epicseed.vampirism.skill.runtime.actions;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.runtime.TargetingResolver;
import com.epicseed.vampirism.hytale.DamageAdapter;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.SkillRequirementEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class DamageActionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private DamageActionHandler() {
    }

    public static boolean dealDamage(Map<String, Object> action, SkillRuntimeContext ctx) {
        List<Ref<EntityStore>> targets = resolveActionTargets(action, ctx);
        if (targets.isEmpty()) {
            LOGGER.atWarning().log("[SkillActionExecutor] dealDamage: no targets resolved");
            return false;
        }

        float baseDamage = resolveDamageAmount(action, ctx);
        boolean selfDamage = Boolean.TRUE.equals(action.get("selfDamage"));
        boolean anyHit = false;

        for (Ref<EntityStore> targetRef : targets) {
            anyHit |= DamageAdapter.executePhysicalDamage(ctx.ref(), targetRef, ctx.store(), baseDamage);
        }

        if (selfDamage) {
            float selfDamageMultiplier = Math.max(0f, ModifierRegistry.get().compute(
                    VampireStatType.SELF_DAMAGE_MULTIPLIER, 1f, ctx.modifierContext()));
            float selfDmg = baseDamage * 0.3f * selfDamageMultiplier;
            if (selfDmg > 0f) {
                DamageAdapter.executePhysicalDamage(ctx.ref(), ctx.ref(), ctx.store(), selfDmg);
            }
        }
        return anyHit;
    }

    public static boolean executeFinalBlow(Map<String, Object> action, SkillRuntimeContext ctx) {
        Ref<EntityStore> targetRef = ctx.targetRef();
        if (targetRef == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: no targetRef in context");
            return false;
        }

        EntityStatValue health = resolveHealth(targetRef, ctx);
        if (health == null) {
            return false;
        }

        Float threshold = resolveExecuteThreshold(action, ctx);
        if (threshold != null) {
            float hpPercent = health.getMax() > 0f ? health.get() / health.getMax() : 0f;
            if (hpPercent > threshold) return false;
        } else {
            String reqId = action.get("requirementId") instanceof String s ? s : null;
            if (reqId != null) {
                Map<String, Object> reqSpec = Map.of("requirementId", reqId);
                if (!SkillRequirementEvaluator.evaluate(reqSpec, ctx)) {
                    LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: requirement not met (" + reqId + ")");
                    return false;
                }
            }
        }

        float executeDamage = Math.max(health.get(), health.getMax()) + 9999f;
        DamageAdapter.executePhysicalDamage(ctx.ref(), targetRef, ctx.store(), executeDamage);
        PassiveService.get().onFeed(ctx);
        LOGGER.atInfo().log("[SkillActionExecutor] executeFinalBlow: target executed");
        return true;
    }

    private static float resolveDamageAmount(Map<String, Object> action, SkillRuntimeContext ctx) {
        float multiplier = action.get("multiplier") instanceof Number m ? m.floatValue() : 1.0f;
        if (action.get("amount") instanceof Number a) {
            return a.floatValue() * multiplier;
        }
        if (action.get("statId") instanceof String statId && !statId.isBlank()) {
            VampireStatType stat = resolveStatType(statId, "dealDamage");
            if (stat == null) return 0f;
            return ModifierRegistry.get().compute(stat, 0f, ctx.modifierContext()) * multiplier;
        }
        return ModifierRegistry.get().compute(VampireStatType.PROJECTILE_DAMAGE, 10f, ctx.modifierContext()) * multiplier;
    }

    private static Float resolveExecuteThreshold(Map<String, Object> action, SkillRuntimeContext ctx) {
        if (action.get("threshold") instanceof Number n) {
            return n.floatValue();
        }
        if (action.get("statId") instanceof String statId && !statId.isBlank()) {
            VampireStatType stat = resolveStatType(statId, "executeFinalBlow");
            if (stat == null) return null;
            return ModifierRegistry.get().compute(stat, 0f, ctx.modifierContext());
        }
        return null;
    }

    private static VampireStatType resolveStatType(String statId, String actionType) {
        try {
            return VampireStatType.valueOf(statId);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] " + actionType + ": unknown statId: " + statId);
            return null;
        }
    }

    private static EntityStatValue resolveHealth(@Nonnull Ref<EntityStore> targetRef, @Nonnull SkillRuntimeContext ctx) {
        EntityStatMap stats = (EntityStatMap) ctx.store().getComponent(targetRef, EntityStatMap.getComponentType());
        if (stats == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: target has no EntityStatMap");
            return null;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: target has no health stat");
        }
        return health;
    }

    private static List<Ref<EntityStore>> resolveActionTargets(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object targetingValue = action.get("targetingId");
        if (targetingValue instanceof String targetingId && !targetingId.isBlank()) {
            var result = TargetingResolver.resolve(Map.of("targetingId", targetingId), ctx);
            if (result.hasTargets()) {
                return result.targets();
            }
        }
        if (ctx.targetRef() != null && !ctx.targetRef().equals(ctx.ref())) {
            return List.of(ctx.targetRef());
        }
        return List.of();
    }
}
