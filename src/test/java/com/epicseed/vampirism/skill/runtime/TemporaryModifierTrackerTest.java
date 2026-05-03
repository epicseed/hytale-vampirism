package com.epicseed.vampirism.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker;
import com.epicseed.vampirism.modifier.VampireStatType;

class TemporaryModifierTrackerTest {

    private static final float EPSILON = 1e-4f;

    @Test
    void directEpicCoreTrackerSupportsVampireStatsAndClearPlayerResetsThem() {
        TemporaryModifierTracker<VampireStatType> tracker = new TemporaryModifierTracker<>();
        UUID playerId = UUID.randomUUID();

        tracker.addModifier(playerId, VampireStatType.SPEED, 0.5f, 60f,
                TemporaryModifierTracker.Stacking.REPLACE, TemporaryModifierTracker.Op.ADDITIVE);

        assertEquals(0.5f, tracker.sumAdditive(playerId, VampireStatType.SPEED), EPSILON);

        tracker.clearPlayer(playerId);

        assertEquals(0f, tracker.sumAdditive(playerId, VampireStatType.SPEED), EPSILON);
    }

    @Test
    void refreshDoesNotDuplicateMatchingBoosts() {
        TemporaryModifierTracker<VampireStatType> tracker = new TemporaryModifierTracker<>();
        UUID playerId = UUID.randomUUID();

        tracker.addModifier(playerId, VampireStatType.SPEED, 0.3f, 60f,
                TemporaryModifierTracker.Stacking.REFRESH, TemporaryModifierTracker.Op.ADDITIVE);
        tracker.addModifier(playerId, VampireStatType.SPEED, 0.3f, 60f,
                TemporaryModifierTracker.Stacking.REFRESH, TemporaryModifierTracker.Op.ADDITIVE);

        assertEquals(0.3f, tracker.sumAdditive(playerId, VampireStatType.SPEED), EPSILON);
    }

    @Test
    void additiveAndMultiplicativeQueriesStayScopedByPlayerAndStat() {
        TemporaryModifierTracker<VampireStatType> tracker = new TemporaryModifierTracker<>();
        UUID primaryPlayer = UUID.randomUUID();
        UUID secondaryPlayer = UUID.randomUUID();

        tracker.addModifier(primaryPlayer, VampireStatType.SPEED, 0.2f, 60f,
                TemporaryModifierTracker.Stacking.STACK, TemporaryModifierTracker.Op.ADDITIVE);
        tracker.addModifier(primaryPlayer, VampireStatType.SPEED, 1.25f, 60f,
                TemporaryModifierTracker.Stacking.STACK, TemporaryModifierTracker.Op.MULTIPLICATIVE);
        tracker.addModifier(secondaryPlayer, VampireStatType.SPEED, 0.4f, 60f,
                TemporaryModifierTracker.Stacking.STACK, TemporaryModifierTracker.Op.ADDITIVE);
        tracker.addModifier(primaryPlayer, VampireStatType.DAMAGE_OUT, 1.5f, 60f,
                TemporaryModifierTracker.Stacking.STACK, TemporaryModifierTracker.Op.MULTIPLICATIVE);

        assertEquals(0.2f, tracker.sumAdditive(primaryPlayer, VampireStatType.SPEED), EPSILON);
        assertEquals(1.25f, tracker.productMultiplicative(primaryPlayer, VampireStatType.SPEED), EPSILON);
        assertEquals(1.5f, tracker.productMultiplicative(primaryPlayer, VampireStatType.DAMAGE_OUT), EPSILON);
        assertEquals(0.4f, tracker.sumAdditive(secondaryPlayer, VampireStatType.SPEED), EPSILON);
    }
}
