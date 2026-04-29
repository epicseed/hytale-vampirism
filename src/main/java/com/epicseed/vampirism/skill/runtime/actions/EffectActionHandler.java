package com.epicseed.vampirism.skill.runtime.actions;

import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.hytale.EffectAdapter;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.model.EffectDef;
import com.epicseed.vampirism.skill.runtime.SkillConditionEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillRequirementEvaluator;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.skill.runtime.TargetingResolver;
import com.epicseed.vampirism.skill.runtime.TargetingResult;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class EffectActionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private EffectActionHandler() {
    }

    public static boolean applyEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object effectValue = action.get("effectId");
        if (!(effectValue instanceof String effectId) || effectId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] applyEffect missing effectId: " + action);
            return false;
        }

        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectId);
        if (effectDef == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Unknown effect definition: " + effectId);
            return false;
        }

        if (!SkillRequirementEvaluator.evaluateAll(effectDef.requirements, ctx)) {
            return false;
        }

        int effectIndex = EffectAdapter.resolveEffectIndex(effectDef.effectId);
        if (effectIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect not found: " + effectDef.effectId);
            return false;
        }

        var effect = EffectAdapter.resolveEffect(effectIndex);
        if (effect == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect asset missing: " + effectDef.effectId);
            return false;
        }

        float duration = resolveEffectDuration(effectDef, ctx);
        if (action.get("durationSeconds") instanceof Number n) {
            duration = n.floatValue();
        }

        List<Ref<EntityStore>> targets = resolveTargets(action, ctx);
        if (targets.isEmpty()) {
            return false;
        }

        String conditionId = action.get("conditionId") instanceof String s ? s : null;
        boolean anyApplied = false;
        for (Ref<EntityStore> targetRef : targets) {
            if (conditionId != null) {
                SkillRuntimeContext targetCtx = ctx.withTarget(targetRef);
                if (!SkillConditionEvaluator.evaluate(Map.of("conditionId", conditionId), targetCtx)) continue;
            }
            anyApplied |= EffectAdapter.applyOrReplace(targetRef, effectIndex, effect, duration, ctx.store());
        }
        return anyApplied;
    }

    public static boolean removeEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object effectValue = action.get("effectId");
        if (!(effectValue instanceof String effectId) || effectId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] removeEffect missing effectId: " + action);
            return false;
        }
        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectId);
        String hytaleEffectId = effectDef != null ? effectDef.effectId : effectId;
        int effectIndex = EffectAdapter.resolveEffectIndex(hytaleEffectId);
        if (effectIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] removeEffect: Hytale effect not found: " + hytaleEffectId);
            return false;
        }

        List<Ref<EntityStore>> targets = resolveTargets(action, ctx);
        if (targets.isEmpty()) {
            return false;
        }

        boolean anyRemoved = false;
        for (Ref<EntityStore> targetRef : targets) {
            anyRemoved |= EffectAdapter.removeIfPresent(targetRef, effectIndex, ctx.store());
        }
        return anyRemoved;
    }

    public static boolean toggleEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object effectValue = action.get("effectId");
        if (!(effectValue instanceof String effectId) || effectId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] toggleEffect missing effectId: " + action);
            return false;
        }

        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectId);
        if (effectDef == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Unknown effect definition: " + effectId);
            return false;
        }

        int effectIndex = EffectAdapter.resolveEffectIndex(effectDef.effectId);
        if (effectIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect not found: " + effectDef.effectId);
            return false;
        }

        var effect = EffectAdapter.resolveEffect(effectIndex);
        if (effect == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect asset missing: " + effectDef.effectId);
            return false;
        }

        Ref<EntityStore> targetRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();
        boolean wasActive = EffectAdapter.hasEffect(targetRef, effectIndex, ctx.store());
        if (wasActive) {
            EffectAdapter.removeIfPresent(targetRef, effectIndex, ctx.store());
        } else if (!EffectAdapter.applyOrReplace(targetRef, effectIndex, effect, resolveEffectDuration(effectDef, ctx), ctx.store())) {
            LOGGER.atWarning().log("[SkillActionExecutor] Target has no EffectControllerComponent for toggleEffect: " + action);
            return false;
        }
        return true;
    }

    private static List<Ref<EntityStore>> resolveTargets(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object targetingIdValue = action.get("targetingId");
        if (targetingIdValue instanceof String targetingId && !targetingId.isBlank()) {
            TargetingResult result = TargetingResolver.resolve(Map.of("targetingId", targetingId), ctx);
            return result.targets();
        }
        Ref<EntityStore> targetRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();
        return List.of(targetRef);
    }

    private static float resolveEffectDuration(@Nonnull EffectDef effectDef, @Nonnull SkillRuntimeContext ctx) {
        if (effectDef.duration <= 0f) {
            return effectDef.duration;
        }
        float multiplier = Math.max(0f, ModifierRegistry.get().compute(
                VampireStatType.ABILITY_DURATION_MULTIPLIER, 1f, ctx.modifierContext()));
        return effectDef.duration * multiplier;
    }
}
