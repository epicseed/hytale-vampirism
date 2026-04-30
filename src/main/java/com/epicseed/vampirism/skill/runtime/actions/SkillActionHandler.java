package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Map;

import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;

@FunctionalInterface
public interface SkillActionHandler extends com.epicseed.epiccore.skill.runtime.actions.SkillActionHandler<SkillRuntimeContext> {
    @Override
    boolean execute(Map<String, Object> action, SkillRuntimeContext ctx);
}
