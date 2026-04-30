package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;

import com.epicseed.vampirism.skill.runtime.actions.AbilityActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.vampirism.skill.runtime.actions.BloodActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ChannelActionHandlers;
import com.epicseed.vampirism.skill.runtime.actions.DamageActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.EffectActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ModifierActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.PresentationActionHandlers;
import com.epicseed.vampirism.skill.runtime.actions.ProjectileActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.StatActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.TeleportActionHandler;
import com.hypixel.hytale.logger.HytaleLogger;

public final class SkillActionExecutor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor<SkillRuntimeContext> EXECUTOR =
            new com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor<>(
                    createActionHandlers(),
                    SkillConditionEvaluator::evaluateAll);

    private SkillActionExecutor() {
    }

    public static boolean executeAll(List<Map<String, Object>> actions, SkillRuntimeContext ctx) {
        return EXECUTOR.executeAll(actions, ctx);
    }

    public static boolean execute(Map<String, Object> action, SkillRuntimeContext ctx) {
        return EXECUTOR.execute(action, ctx);
    }

    public static com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor<SkillRuntimeContext> sharedExecutor() {
        return EXECUTOR;
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
}
