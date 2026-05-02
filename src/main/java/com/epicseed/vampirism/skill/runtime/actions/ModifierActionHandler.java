package com.epicseed.vampirism.skill.runtime.actions;

import java.util.LinkedHashMap;
import java.util.Map;

import com.epicseed.vampirism.skill.runtime.SkillActionExecutor;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;

public final class ModifierActionHandler {

    private ModifierActionHandler() {
    }

    public static boolean grantTemporaryModifierLegacySpeed(Map<String, Object> action, SkillRuntimeContext ctx) {
        Map<String, Object> rewritten = new LinkedHashMap<>(action);
        rewritten.put("type", "grantTemporaryModifier");
        rewritten.putIfAbsent("statId", "SPEED");
        Object boostStat = rewritten.remove("speedBoostStatId");
        if (boostStat != null) {
            rewritten.putIfAbsent("amountStatId", boostStat);
        }
        return SkillActionExecutor.execute(rewritten, ctx);
    }
}
