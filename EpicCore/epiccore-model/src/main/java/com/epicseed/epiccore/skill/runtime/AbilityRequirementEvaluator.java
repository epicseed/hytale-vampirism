package com.epicseed.epiccore.skill.runtime;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface AbilityRequirementEvaluator<CTX> {
    boolean evaluateAll(List<Map<String, Object>> requirements, CTX context);
}
