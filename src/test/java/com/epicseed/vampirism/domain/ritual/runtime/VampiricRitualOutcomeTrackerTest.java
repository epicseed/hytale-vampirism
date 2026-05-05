package com.epicseed.vampirism.domain.ritual.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

class VampiricRitualOutcomeTrackerTest {

    @Test
    void mapsRuntimePhasesToAnchorStates() {
        assertEquals("prepared", VampiricRitualAnchorState.fromSnapshot(snapshot(VampiricRitualRuntimePhase.PREPARING)).displayName());
        assertEquals("binding", VampiricRitualAnchorState.fromSnapshot(snapshot(VampiricRitualRuntimePhase.BINDING)).displayName());
        assertEquals("active", VampiricRitualAnchorState.fromSnapshot(snapshot(VampiricRitualRuntimePhase.CHANNELING)).displayName());
        assertEquals("collapse", VampiricRitualAnchorState.fromSnapshot(snapshot(VampiricRitualRuntimePhase.COLLAPSE)).displayName());
    }

    @Test
    void expiresOldOutcomes() {
        UUID uuid = UUID.randomUUID();
        VampiricRitualOutcomeTracker.recordBacklash(uuid, "awakening", 3, VampiricRitualRuntimePhase.UNSTABLE, 2);
        assertTrue(VampiricRitualOutcomeTracker.recentOutcome(uuid, System.currentTimeMillis()).isPresent());
        assertFalse(VampiricRitualOutcomeTracker.recentOutcome(uuid, System.currentTimeMillis() + 60_000L).isPresent());
    }

    @Test
    void describesCollapseOutcome() {
        UUID uuid = UUID.randomUUID();
        VampiricRitualOutcomeTracker.recordCollapse(uuid, "awakening", 7, 4, 88d);
        String summary = VampiricRitualOutcomeTracker.describeOutcome(
                VampiricRitualOutcomeTracker.recentOutcome(uuid).orElseThrow());
        assertTrue(summary.contains("collapse"));
        assertTrue(summary.contains("7 blood"));
        assertTrue(summary.contains("88%"));
        VampiricRitualOutcomeTracker.clearPlayer(uuid);
    }

    private static VampiricRitualRuntimeSnapshot snapshot(VampiricRitualRuntimePhase phase) {
        return new VampiricRitualRuntimeSnapshot(
                "awakening",
                "Crimson Awakening",
                "Furniture_Ancient_Coffin",
                new Vector3i(0, 0, 0),
                new Vector3d(0d, 0d, 0d),
                phase,
                phase.active(),
                3,
                5,
                80d,
                65d,
                20d,
                1.5d,
                2,
                2d,
                8d,
                List.of(new VampiricRitualPointState(
                        "north",
                        new Vector3d(0d, 0.15d, -3d),
                        false,
                        0.8d,
                        "fang_wake",
                        "Fang Wake",
                        1,
                        4,
                        false,
                        List.of(new Vector3d(0d, 0.1d, -2.7d)))));
    }
}
