package com.epicseed.epiccore.skill.runtime.actions;

import java.util.List;
import java.util.Map;

@FunctionalInterface
public interface ActionConditionEvaluator<CTX> {
    boolean evaluateAll(List<Map<String, Object>> conditions, CTX context);
}
