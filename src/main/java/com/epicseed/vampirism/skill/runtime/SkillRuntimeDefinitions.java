package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.runtime.ReusableDefinitionProvider;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkillRuntimeDefinitions {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static ReusableDefinitionProvider provider = (kind, id) -> null;

    private SkillRuntimeDefinitions() {}

    public static void init(ReusableDefinitionProvider provider) {
        SkillRuntimeDefinitions.provider = provider != null ? provider : (kind, id) -> null;
    }

    public static Map<String, Object> resolveCondition(Map<String, Object> spec) {
        return resolve(spec, "conditionId", "condition");
    }

    public static Map<String, Object> resolveRequirement(Map<String, Object> spec) {
        return resolve(spec, "requirementId", "requirement");
    }

    public static Map<String, Object> resolveTrigger(Map<String, Object> spec) {
        return resolve(spec, "triggerId", "trigger");
    }

    public static Map<String, Object> resolveAction(Map<String, Object> spec) {
        return resolve(spec, "actionId", "action");
    }

    public static Map<String, Object> resolveTargeting(Map<String, Object> spec) {
        return resolve(spec, "targetingId", "targeting");
    }

    private static Map<String, Object> resolve(Map<String, Object> spec, String refKey, String kind) {
        if (spec == null || spec.isEmpty()) return Collections.emptyMap();
        if (spec.containsKey("type")) return new LinkedHashMap<>(spec);

        Object refValue = spec.get(refKey);
        if (!(refValue instanceof String refId) || refId.isBlank()) {
            return new LinkedHashMap<>(spec);
        }

        ReusableDef entry = provider.get(kind, refId);
        if (entry == null) {
            LOGGER.atWarning().log("[SkillRuntimeDefinitions] Unknown " + refKey + ": " + refId);
            return new LinkedHashMap<>(spec);
        }

        Map<String, Object> resolved = new LinkedHashMap<>(entry.copyDefinition());
        for (Map.Entry<String, Object> value : spec.entrySet()) {
            if (!refKey.equals(value.getKey())) {
                resolved.put(value.getKey(), value.getValue());
            }
        }
        return resolved;
    }
}
