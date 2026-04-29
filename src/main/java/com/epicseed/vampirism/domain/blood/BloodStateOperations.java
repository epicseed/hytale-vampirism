package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;

final class BloodStateOperations {
    private BloodStateOperations() {
    }

    static BloodState spend(@Nonnull BloodState state, int amount) {
        if (amount <= 0) {
            return state;
        }
        state.blood = Math.max(0, state.blood - amount);
        return state;
    }

    static BloodState add(@Nonnull BloodState state, int amount, int recoveryThreshold) {
        if (amount <= 0) {
            return state;
        }
        state.blood = Math.min(maxBlood(state), state.blood + amount);
        if (state.isStarving && state.blood >= recoveryThreshold) {
            state.isStarving = false;
        }
        return state;
    }

    static void refreshCapacity(@Nonnull BloodState state, int newCapacity) {
        state.maxBlood = Math.max(1, newCapacity);
        clampBlood(state);
    }

    static void clampBlood(@Nonnull BloodState state) {
        state.maxBlood = maxBlood(state);
        state.blood = Math.max(0, Math.min(state.blood, state.maxBlood));
    }

    static boolean isOverfed(@Nonnull BloodState state) {
        return state.blood >= maxBlood(state);
    }

    static boolean isNormal(@Nonnull BloodState state, int starvingThreshold) {
        return state.blood > starvingThreshold && state.blood < maxBlood(state);
    }

    private static int maxBlood(@Nonnull BloodState state) {
        return Math.max(1, state.maxBlood);
    }
}
