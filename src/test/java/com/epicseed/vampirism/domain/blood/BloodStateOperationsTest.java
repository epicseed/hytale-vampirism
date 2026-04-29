package com.epicseed.vampirism.domain.blood;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BloodStateOperationsTest {

    @Test
    void spendDoesNotDropBelowZero() {
        BloodState state = state(20, 100, false);

        BloodState result = BloodStateOperations.spend(state, 50);

        assertSame(state, result);
        assertEquals(0, state.blood);
    }

    @Test
    void spendIgnoresZeroAndNegativeAmounts() {
        BloodState state = state(20, 100, false);

        BloodStateOperations.spend(state, 0);
        BloodStateOperations.spend(state, -5);

        assertEquals(20, state.blood);
    }

    @Test
    void addDoesNotExceedMaxBlood() {
        BloodState state = state(90, 100, false);

        BloodState result = BloodStateOperations.add(state, 25, 50);

        assertSame(state, result);
        assertEquals(100, state.blood);
    }

    @Test
    void addIgnoresZeroAndNegativeAmounts() {
        BloodState state = state(20, 100, true);

        BloodStateOperations.add(state, 0, 50);
        BloodStateOperations.add(state, -5, 50);

        assertEquals(20, state.blood);
        assertTrue(state.isStarving);
    }

    @Test
    void addClearsStarvingAtRecoveryThreshold() {
        BloodState state = state(45, 100, true);

        BloodStateOperations.add(state, 5, 50);

        assertEquals(50, state.blood);
        assertFalse(state.isStarving);
    }

    @Test
    void refreshCapacityClampsCurrentBlood() {
        BloodState state = state(80, 100, false);

        BloodStateOperations.refreshCapacity(state, 60);

        assertEquals(60, state.maxBlood);
        assertEquals(60, state.blood);
    }

    @Test
    void refreshCapacityKeepsAtLeastOneMaxBlood() {
        BloodState state = state(80, 100, false);

        BloodStateOperations.refreshCapacity(state, 0);

        assertEquals(1, state.maxBlood);
        assertEquals(1, state.blood);
    }

    @Test
    void overfedAndNormalUseStateThresholds() {
        BloodState normal = state(70, 100, false);
        BloodState overfed = state(100, 100, false);
        BloodState starving = state(20, 100, true);

        assertTrue(BloodStateOperations.isNormal(normal, 30));
        assertFalse(BloodStateOperations.isOverfed(normal));
        assertTrue(BloodStateOperations.isOverfed(overfed));
        assertFalse(BloodStateOperations.isNormal(overfed, 30));
        assertFalse(BloodStateOperations.isNormal(starving, 30));
    }

    private static BloodState state(int blood, int maxBlood, boolean starving) {
        BloodState state = new BloodState();
        state.blood = blood;
        state.maxBlood = maxBlood;
        state.isStarving = starving;
        return state;
    }
}
