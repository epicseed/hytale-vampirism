package com.epicseed.vampirism.domain.hunt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class HuntModelTest {

    @Test
    void huntPhaseOrderMatchesStateMachineFlow() {
        assertArrayEquals(new HuntPhase[] {
                HuntPhase.IDLE,
                HuntPhase.ROUTE_PENDING,
                HuntPhase.APPROACHING,
                HuntPhase.GUIDING,
                HuntPhase.SUMMONING,
                HuntPhase.PREY_ACTIVE
        }, HuntPhase.values());
    }

    @Test
    void idleDebugInfoUsesInactiveDefaults() {
        NightHuntDebugInfo info = NightHuntDebugInfo.idle();

        assertEquals("idle", info.phase());
        assertFalse(info.active());
        assertEquals(0f, info.cooldownRemainingSeconds());
        assertEquals(0f, info.idleDelayRemainingSeconds());
        assertEquals(0, info.completedWaypoints());
        assertEquals(1, info.targetWaypoints());
        assertEquals(0, info.bonusWaypoints());
        assertEquals(1, info.visualTier());
        assertFalse(info.forced());
        assertFalse(info.preyActive());
    }
}
