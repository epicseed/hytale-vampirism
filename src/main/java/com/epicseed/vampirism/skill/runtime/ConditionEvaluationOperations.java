package com.epicseed.vampirism.skill.runtime;

final class ConditionEvaluationOperations {
    private ConditionEvaluationOperations() {
    }

    static boolean compare(String op, float current, float value) {
        return com.epicseed.epiccore.skill.runtime.ConditionEvaluationOperations.compare(op, current, value);
    }

    static boolean isCompareOperatorSupported(String op) {
        return com.epicseed.epiccore.skill.runtime.ConditionEvaluationOperations.isCompareOperatorSupported(op);
    }

    static float normalizeBloodValue(Number value, int baseCapacityUnits) {
        return com.epicseed.epiccore.skill.runtime.ConditionEvaluationOperations.normalizeBloodValue(value, baseCapacityUnits);
    }

    static boolean evaluateStateOperator(String operator, boolean active) {
        return com.epicseed.epiccore.skill.runtime.ConditionEvaluationOperations.evaluateStateOperator(operator, active);
    }

    static boolean isStateOperatorSupported(String operator) {
        return com.epicseed.epiccore.skill.runtime.ConditionEvaluationOperations.isStateOperatorSupported(operator);
    }
}
