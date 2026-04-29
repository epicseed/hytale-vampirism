package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.skill.runtime.actions.DamageActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.EffectActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.AbilityActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.vampirism.skill.runtime.actions.BloodActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ChannelActionHandlers;
import com.epicseed.vampirism.skill.runtime.actions.ModifierActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.PresentationActionHandlers;
import com.epicseed.vampirism.skill.runtime.actions.ProjectileActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.SkillActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.StatActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.TeleportActionHandler;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

public final class SkillActionExecutor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final ActionHandlerRegistry ACTION_HANDLERS = createActionHandlers();

    private SkillActionExecutor() {}

    public static boolean executeAll(List<Map<String, Object>> actions, SkillRuntimeContext ctx) {
        if (actions == null || actions.isEmpty()) return false;
        boolean executedAny = false;
        for (Map<String, Object> action : actions) {
            executedAny |= execute(action, ctx);
        }
        return executedAny;
    }

    public static boolean execute(Map<String, Object> action, SkillRuntimeContext ctx) {
        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveAction(action);
        Object type = resolved.get("type");
        if (!(type instanceof String typeId) || typeId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] Action without type: " + resolved);
            return false;
        }
        if (!evaluateActionConditions(resolved, ctx)) {
            return false;
        }

        SkillActionHandler handler = ACTION_HANDLERS.find(typeId);
        if (handler == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Unsupported action type: " + typeId);
            return false;
        }
        return handler.execute(resolved, ctx);
    }

    private static ActionHandlerRegistry createActionHandlers() {
        return new ActionHandlerRegistry()
                .register("applyEffect", EffectActionHandler::applyEffect)
                .register("removeEffect", EffectActionHandler::removeEffect)
                .register("toggleEffect", EffectActionHandler::toggleEffect)
                .register("healSelf", StatActionHandler::healSelf)
                .register("dealDamage", DamageActionHandler::dealDamage)
                .register("executeFinalBlow", DamageActionHandler::executeFinalBlow)
                .register("startFeedChannel", ChannelActionHandlers::startFeedChannel)
                .register("startHealthToBloodChannel", ChannelActionHandlers::startHealthToBloodChannel)
                .register("spawnProjectile", ProjectileActionHandler::spawnProjectile)
                .register("teleport", TeleportActionHandler::teleport)
                .register("activateAbility", AbilityActionHandler::activateAbility)
                .register("grantTemporaryModifier", ModifierActionHandler::grantTemporaryModifier)
                .register("modifyStat", StatActionHandler::modifyStat)
                .register("modifyBlood", BloodActionHandler::modifyBlood)
                .register("playSound", PresentationActionHandlers::playSound)
                .register("spawnParticles", PresentationActionHandlers::spawnParticles)
                .register("sendMessage", PresentationActionHandlers::sendMessage)
                .register("applyTimedSpeedBoost", ModifierActionHandler::grantTemporaryModifierLegacySpeed)
                .register("applyControlEffect", SkillActionExecutor::deprecatedApplyControlEffect)
                .register("highlightEnemies", SkillActionExecutor::deprecatedHighlightEnemies);
    }

    private static boolean deprecatedApplyControlEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        LOGGER.atWarning().log("[SkillActionExecutor] 'applyControlEffect' is deprecated; migrate to 'applyEffect' with an effectId + targetingId: " + action);
        return false;
    }

    private static boolean deprecatedHighlightEnemies(Map<String, Object> action, SkillRuntimeContext ctx) {
        LOGGER.atWarning().log("[SkillActionExecutor] 'highlightEnemies' is deprecated; migrate to 'applyEffect' with an effectId + targetingId: " + action);
        return false;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static boolean evaluateActionConditions(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object conditionsObj = action.get("conditions");
        if (!(conditionsObj instanceof List<?> rawConditions) || rawConditions.isEmpty()) {
            return true;
        }
        try {
            return SkillConditionEvaluator.evaluateAll((List<Map<String, Object>>) rawConditions, ctx);
        } catch (ClassCastException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] Malformed action conditions: " + action);
            return false;
        }
    }

}
