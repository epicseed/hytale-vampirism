package com.epicseed.vampirism.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.modifier.VampireStatType;

class TemporaryModifierTrackerTest {

    private static final float EPSILON = 1e-4f;

    @AfterEach
    void clearTrackedPlayers() {
        // Each test uses distinct UUIDs, but clearing the static tracker keeps
        // the suite deterministic when tests are re-ordered.
        TemporaryModifierTracker.clearPlayer(primaryPlayer);
        TemporaryModifierTracker.clearPlayer(secondaryPlayer);
    }

    private final UUID primaryPlayer = UUID.randomUUID();
    private final UUID secondaryPlayer = UUID.randomUUID();

    @Test
    void legacySpeedFacadeUsesSpeedStatAndClearPlayerResetsIt() {
        TemporaryModifierTracker.addBoost(primaryPlayer, VampireStatType.SPEED, 0.5f, 60f);

        assertEquals(0.5f, TemporaryModifierTracker.getBoost(primaryPlayer), EPSILON);
        assertTrue(TemporaryModifierTracker.hasBoost(primaryPlayer));

        TemporaryModifierTracker.clearPlayer(primaryPlayer);

        assertEquals(0f, TemporaryModifierTracker.getBoost(primaryPlayer), EPSILON);
        assertFalse(TemporaryModifierTracker.hasBoost(primaryPlayer));
    }

    @Test
    void refreshDoesNotDuplicateMatchingBoosts() {
        TemporaryModifierTracker.addBoost(primaryPlayer, VampireStatType.SPEED, 0.3f, 60f,
                TemporaryModifierTracker.Stacking.REFRESH, TemporaryModifierTracker.Op.ADDITIVE);
        TemporaryModifierTracker.addBoost(primaryPlayer, VampireStatType.SPEED, 0.3f, 60f,
                TemporaryModifierTracker.Stacking.REFRESH, TemporaryModifierTracker.Op.ADDITIVE);

        assertEquals(0.3f, TemporaryModifierTracker.sumAdditive(primaryPlayer, VampireStatType.SPEED), EPSILON);
    }

    @Test
    void additiveAndMultiplicativeQueriesStayScopedByPlayerAndStat() {
        TemporaryModifierTracker.addBoost(primaryPlayer, VampireStatType.SPEED, 0.2f, 60f,
                TemporaryModifierTracker.Stacking.STACK, TemporaryModifierTracker.Op.ADDITIVE);
        TemporaryModifierTracker.addBoost(primaryPlayer, VampireStatType.SPEED, 1.25f, 60f,
                TemporaryModifierTracker.Stacking.STACK, TemporaryModifierTracker.Op.MULTIPLICATIVE);
        TemporaryModifierTracker.addBoost(secondaryPlayer, VampireStatType.SPEED, 0.4f, 60f,
                TemporaryModifierTracker.Stacking.STACK, TemporaryModifierTracker.Op.ADDITIVE);
        TemporaryModifierTracker.addBoost(primaryPlayer, VampireStatType.DAMAGE_OUT, 1.5f, 60f,
                TemporaryModifierTracker.Stacking.STACK, TemporaryModifierTracker.Op.MULTIPLICATIVE);

        assertEquals(0.2f, TemporaryModifierTracker.sumAdditive(primaryPlayer, VampireStatType.SPEED), EPSILON);
        assertEquals(1.25f, TemporaryModifierTracker.productMultiplicative(primaryPlayer, VampireStatType.SPEED), EPSILON);
        assertEquals(1.5f, TemporaryModifierTracker.productMultiplicative(primaryPlayer, VampireStatType.DAMAGE_OUT), EPSILON);
        assertEquals(0.4f, TemporaryModifierTracker.sumAdditive(secondaryPlayer, VampireStatType.SPEED), EPSILON);
    }
}
