package com.epicseed.vampirism.skill.runtime;

final class ConditionEvaluationOperations {
    private static final float FLOAT_EQUALITY_TOLERANCE = 0.0001f;

    private ConditionEvaluationOperations() {
    }

    static boolean compare(String op, float current, float value) {
        return switch (op) {
            case "gte", ">=" -> current >= value;
            case "lte", "<=" -> current <= value;
            case "gt", ">" -> current > value;
            case "lt", "<" -> current < value;
            case "eq", "==", "=" -> Math.abs(current - value) < FLOAT_EQUALITY_TOLERANCE;
            default -> false;
        };
    }

    static boolean isCompareOperatorSupported(String op) {
        return switch (op) {
            case "gte", ">=", "lte", "<=", "gt", ">", "lt", "<", "eq", "==", "=" -> true;
            default -> false;
        };
    }

    static float normalizeBloodValue(Number value, int baseCapacityUnits) {
        float raw = value.floatValue();
        if (Math.abs(raw) <= 1f) {
            raw *= baseCapacityUnits;
        }
        return raw;
    }

    static boolean evaluateStateOperator(String operator, boolean active) {
        return switch (operator) {
            case "isTrue" -> active;
            case "isFalse" -> !active;
            default -> false;
        };
    }

    static boolean isStateOperatorSupported(String operator) {
        return switch (operator) {
            case "isTrue", "isFalse" -> true;
            default -> false;
        };
    }
}
