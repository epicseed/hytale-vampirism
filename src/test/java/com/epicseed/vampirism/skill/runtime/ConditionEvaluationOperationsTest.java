package com.epicseed.vampirism.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ConditionEvaluationOperationsTest {

    @Test
    void compareSupportsGreaterThanOrEqualAliases() {
        assertTrue(ConditionEvaluationOperations.compare("gte", 10f, 10f));
        assertTrue(ConditionEvaluationOperations.compare(">=", 10f, 9f));
        assertFalse(ConditionEvaluationOperations.compare("gte", 9f, 10f));
    }

    @Test
    void compareSupportsLessThanOrEqualAliases() {
        assertTrue(ConditionEvaluationOperations.compare("lte", 10f, 10f));
        assertTrue(ConditionEvaluationOperations.compare("<=", 9f, 10f));
        assertFalse(ConditionEvaluationOperations.compare("lte", 10f, 9f));
    }

    @Test
    void compareSupportsStrictAliases() {
        assertTrue(ConditionEvaluationOperations.compare("gt", 11f, 10f));
        assertTrue(ConditionEvaluationOperations.compare(">", 11f, 10f));
        assertTrue(ConditionEvaluationOperations.compare("lt", 9f, 10f));
        assertTrue(ConditionEvaluationOperations.compare("<", 9f, 10f));
        assertFalse(ConditionEvaluationOperations.compare("gt", 10f, 10f));
        assertFalse(ConditionEvaluationOperations.compare("lt", 10f, 10f));
    }

    @Test
    void compareSupportsEqualityAliasesWithTolerance() {
        assertTrue(ConditionEvaluationOperations.compare("eq", 10.00001f, 10f));
        assertTrue(ConditionEvaluationOperations.compare("==", 10.00001f, 10f));
        assertTrue(ConditionEvaluationOperations.compare("=", 10.00001f, 10f));
        assertFalse(ConditionEvaluationOperations.compare("eq", 10.001f, 10f));
    }

    @Test
    void compareRejectsUnsupportedOperators() {
        assertFalse(ConditionEvaluationOperations.isCompareOperatorSupported("between"));
        assertFalse(ConditionEvaluationOperations.compare("between", 10f, 10f));
    }

    @Test
    void normalizeBloodValueTreatsUnitScaleAsCapacityFraction() {
        assertEquals(50f, ConditionEvaluationOperations.normalizeBloodValue(0.5f, 100));
        assertEquals(100f, ConditionEvaluationOperations.normalizeBloodValue(1f, 100));
        assertEquals(-50f, ConditionEvaluationOperations.normalizeBloodValue(-0.5f, 100));
    }

    @Test
    void normalizeBloodValueKeepsAbsoluteValuesOutsideUnitScale() {
        assertEquals(2f, ConditionEvaluationOperations.normalizeBloodValue(2f, 100));
        assertEquals(-2f, ConditionEvaluationOperations.normalizeBloodValue(-2f, 100));
    }

    @Test
    void stateOperatorSupportsTrueAndFalseChecks() {
        assertTrue(ConditionEvaluationOperations.evaluateStateOperator("isTrue", true));
        assertFalse(ConditionEvaluationOperations.evaluateStateOperator("isTrue", false));
        assertTrue(ConditionEvaluationOperations.evaluateStateOperator("isFalse", false));
        assertFalse(ConditionEvaluationOperations.evaluateStateOperator("isFalse", true));
    }

    @Test
    void stateOperatorRejectsUnsupportedValues() {
        assertFalse(ConditionEvaluationOperations.isStateOperatorSupported("unknown"));
        assertFalse(ConditionEvaluationOperations.evaluateStateOperator("unknown", true));
    }
}
