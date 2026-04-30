package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.resource.ResourceStateMath;

final class BloodStateOperations {
    private BloodStateOperations() {
    }

    static BloodState spend(@Nonnull BloodState state, int amount) {
        if (amount <= 0) {
            return state;
        }
        state.blood = ResourceStateMath.spend(state.blood, amount);
        return state;
    }

    static BloodState add(@Nonnull BloodState state, int amount, int recoveryThreshold) {
        if (amount <= 0) {
            return state;
        }
        state.blood = ResourceStateMath.add(state.blood, state.maxBlood, amount);
        if (state.isStarving && state.blood >= recoveryThreshold) {
            state.isStarving = false;
        }
        return state;
    }

    static void refreshCapacity(@Nonnull BloodState state, int newCapacity) {
        state.maxBlood = ResourceStateMath.sanitizeMax(newCapacity);
        clampBlood(state);
    }

    static void clampBlood(@Nonnull BloodState state) {
        state.maxBlood = ResourceStateMath.sanitizeMax(state.maxBlood);
        state.blood = ResourceStateMath.clampCurrent(state.blood, state.maxBlood);
    }

    static boolean isOverfed(@Nonnull BloodState state) {
        return state.blood >= ResourceStateMath.sanitizeMax(state.maxBlood);
    }

    static boolean isNormal(@Nonnull BloodState state, int starvingThreshold) {
        return state.blood > starvingThreshold && state.blood < ResourceStateMath.sanitizeMax(state.maxBlood);
    }
}
