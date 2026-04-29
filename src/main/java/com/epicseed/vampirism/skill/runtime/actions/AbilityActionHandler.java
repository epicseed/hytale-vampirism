package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Map;

import com.epicseed.vampirism.skill.runtime.AbilityService;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.hypixel.hytale.logger.HytaleLogger;

public final class AbilityActionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private AbilityActionHandler() {
    }

    public static boolean activateAbility(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object abilityIdObj = action.get("abilityId");
        if (!(abilityIdObj instanceof String abilityId) || abilityId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] activateAbility missing abilityId: " + action);
            return false;
        }
        if (ctx.uuid() == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] activateAbility: no UUID in context");
            return false;
        }

        var result = AbilityService.activateFromAction(abilityId, ctx);
        if (result.isFailure()) {
            LOGGER.atInfo().log("[SkillActionExecutor] activateAbility '" + abilityId + "': " + result.reason());
        }
        return result.isSuccess();
    }
}
