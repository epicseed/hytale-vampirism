package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Map;

import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;

@FunctionalInterface
public interface SkillActionHandler {
    boolean execute(Map<String, Object> action, SkillRuntimeContext ctx);
}
