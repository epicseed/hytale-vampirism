package com.epicseed.epiccore.skill.runtime.actions;

import java.util.Map;

@FunctionalInterface
public interface SkillActionHandler<CTX> {
    boolean execute(Map<String, Object> action, CTX context);
}
