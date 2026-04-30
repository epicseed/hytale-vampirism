package com.epicseed.vampirism.skill.runtime;

import java.util.Map;

import com.epicseed.epiccore.skill.runtime.ReusableDefinitionProvider;

public final class SkillRuntimeDefinitions {

    private SkillRuntimeDefinitions() {
    }

    public static void init(ReusableDefinitionProvider provider) {
        com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions.init(provider);
    }

    public static Map<String, Object> resolveCondition(Map<String, Object> spec) {
        return com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions.resolveCondition(spec);
    }

    public static Map<String, Object> resolveRequirement(Map<String, Object> spec) {
        return com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions.resolveRequirement(spec);
    }

    public static Map<String, Object> resolveTrigger(Map<String, Object> spec) {
        return com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions.resolveTrigger(spec);
    }

    public static Map<String, Object> resolveAction(Map<String, Object> spec) {
        return com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions.resolveAction(spec);
    }

    public static Map<String, Object> resolveTargeting(Map<String, Object> spec) {
        return com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions.resolveTargeting(spec);
    }
}
