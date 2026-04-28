package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.skill.model.ReusableDef;
import com.epicseed.vampirism.skill.registry.ReusableDefRegistry;
import com.hypixel.hytale.logger.HytaleLogger;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SkillRuntimeDefinitions {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private SkillRuntimeDefinitions() {}

    public static Map<String, Object> resolveCondition(Map<String, Object> spec) {
        return resolve(spec, "conditionId", registryOrNull("condition"));
    }

    public static Map<String, Object> resolveRequirement(Map<String, Object> spec) {
        return resolve(spec, "requirementId", registryOrNull("requirement"));
    }

    public static Map<String, Object> resolveTrigger(Map<String, Object> spec) {
        return resolve(spec, "triggerId", registryOrNull("trigger"));
    }

    public static Map<String, Object> resolveAction(Map<String, Object> spec) {
        return resolve(spec, "actionId", registryOrNull("action"));
    }

    public static Map<String, Object> resolveTargeting(Map<String, Object> spec) {
        return resolve(spec, "targetingId", registryOrNull("targeting"));
    }

    private static ReusableDefRegistry registryOrNull(String kind) {
        Vampirism plugin = Vampirism.getInstance();
        return switch (kind) {
            case "condition" -> plugin.GetConditionRegistry();
            case "requirement" -> plugin.GetRequirementRegistry();
            case "trigger" -> plugin.GetTriggerRegistry();
            case "action" -> plugin.GetActionRegistry();
            case "targeting" -> plugin.GetTargetingRegistry();
            default -> null;
        };
    }

    private static Map<String, Object> resolve(Map<String, Object> spec, String refKey, ReusableDefRegistry registry) {
        if (spec == null || spec.isEmpty()) return Collections.emptyMap();
        if (spec.containsKey("type")) return new LinkedHashMap<>(spec);
        if (registry == null) return new LinkedHashMap<>(spec);

        Object refValue = spec.get(refKey);
        if (!(refValue instanceof String refId) || refId.isBlank()) {
            return new LinkedHashMap<>(spec);
        }

        ReusableDef entry = registry.Get(refId);
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
