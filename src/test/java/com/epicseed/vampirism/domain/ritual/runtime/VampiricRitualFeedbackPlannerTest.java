package com.epicseed.vampirism.domain.ritual.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualFeedbackPlanner.RitualCue;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

class VampiricRitualFeedbackPlannerTest {

    @Test
    void detectsTraceStartAndCadence() {
        VampiricRitualRuntimeSnapshot tracingSnapshot = snapshot(
                VampiricRitualRuntimePhase.PREPARING,
                List.of(tracingPoint("north", 2)));

        var firstPlan = VampiricRitualFeedbackPlanner.plan(null, tracingSnapshot, 100L);
        assertTrue(firstPlan.cues().contains(RitualCue.TRACE_STARTED));

        VampiricRitualRuntimeSnapshot advancedTracingSnapshot = snapshot(
                VampiricRitualRuntimePhase.PREPARING,
                List.of(tracingPoint("north", 5)));
        var cadencePlan = VampiricRitualFeedbackPlanner.plan(firstPlan.nextState(), advancedTracingSnapshot, 400L);
        assertTrue(cadencePlan.cues().contains(RitualCue.TRACE_CADENCE));
    }

    @Test
    void detectsSealAndPhaseTransitions() {
        VampiricRitualRuntimeSnapshot sealedSnapshot = snapshot(
                VampiricRitualRuntimePhase.PREPARING,
                List.of(activePoint("north"), inactivePoint("south")));
        var sealedPlan = VampiricRitualFeedbackPlanner.plan(null, sealedSnapshot, 10L);
        assertTrue(sealedPlan.cues().contains(RitualCue.GLYPH_SEALED));

        VampiricRitualRuntimeSnapshot bindingSnapshot = snapshot(
                VampiricRitualRuntimePhase.BINDING,
                List.of(activePoint("north"), activePoint("south")));
        var bindingPlan = VampiricRitualFeedbackPlanner.plan(sealedPlan.nextState(), bindingSnapshot, 50L);
        assertTrue(bindingPlan.cues().contains(RitualCue.GLYPH_SEALED));
        assertTrue(bindingPlan.cues().contains(RitualCue.PHASE_BINDING));

        VampiricRitualRuntimeSnapshot unstableSnapshot = snapshot(
                VampiricRitualRuntimePhase.UNSTABLE,
                List.of(activePoint("north"), activePoint("south")));
        var unstablePlan = VampiricRitualFeedbackPlanner.plan(bindingPlan.nextState(), unstableSnapshot, 200L);
        assertTrue(unstablePlan.cues().contains(RitualCue.PHASE_UNSTABLE));

        VampiricRitualRuntimeSnapshot channelingSnapshot = snapshot(
                VampiricRitualRuntimePhase.CHANNELING,
                List.of(activePoint("north"), activePoint("south")));
        var steadiedPlan = VampiricRitualFeedbackPlanner.plan(unstablePlan.nextState(), channelingSnapshot, 500L);
        assertTrue(steadiedPlan.cues().contains(RitualCue.PHASE_STEADIED));
    }

    @Test
    void detectsRejectedTraceAndSuppressesTerminalClear() {
        VampiricRitualRuntimeSnapshot tracingSnapshot = snapshot(
                VampiricRitualRuntimePhase.PREPARING,
                List.of(tracingPoint("north", 3)));
        var tracingState = VampiricRitualFeedbackPlanner.plan(null, tracingSnapshot, 100L).nextState();

        VampiricRitualRuntimeSnapshot rejectedSnapshot = snapshot(
                VampiricRitualRuntimePhase.PREPARING,
                List.of(inactivePoint("north")));
        var rejectedPlan = VampiricRitualFeedbackPlanner.plan(tracingState, rejectedSnapshot, 220L);
        assertTrue(rejectedPlan.cues().contains(RitualCue.TRACE_REJECTED));

        VampiricRitualRuntimeSnapshot successSnapshot = snapshot(
                VampiricRitualRuntimePhase.SUCCESS,
                List.of(activePoint("north")));
        var successState = VampiricRitualFeedbackPlanner.plan(null, successSnapshot, 300L).nextState();
        var clearedAfterSuccess = VampiricRitualFeedbackPlanner.plan(successState, null, 800L);

        assertEquals(List.of(), clearedAfterSuccess.cues());
        assertFalse(clearedAfterSuccess.nextState() != null);
    }

    private static VampiricRitualRuntimeSnapshot snapshot(VampiricRitualRuntimePhase phase,
                                                          List<VampiricRitualPointState> points) {
        long activePoints = points.stream().filter(VampiricRitualPointState::active).count();
        return new VampiricRitualRuntimeSnapshot(
                "awakening",
                "Crimson Awakening",
                "Furniture_Ancient_Coffin",
                new Vector3i(0, 0, 0),
                new Vector3d(0d, 0d, 0d),
                phase,
                phase.active(),
                (int) activePoints,
                points.size(),
                88d,
                70d,
                18d,
                1.8d,
                0,
                2.0d,
                8.0d,
                points);
    }

    private static VampiricRitualPointState activePoint(String id) {
        return new VampiricRitualPointState(
                id,
                new Vector3d(0d, 0.15d, 0d),
                true,
                0.96d,
                "fang_wake",
                "fang_wake",
                4,
                4,
                false,
                List.of(new Vector3d(0d, 0.15d, 0d)),
                List.of());
    }

    private static VampiricRitualPointState inactivePoint(String id) {
        return new VampiricRitualPointState(
                id,
                new Vector3d(0d, 0.15d, 0d),
                false,
                0.0d,
                "fang_wake",
                "fang_wake",
                0,
                4,
                false,
                List.of(new Vector3d(0d, 0.15d, 0d)),
                List.of());
    }

    private static VampiricRitualPointState tracingPoint(String id, int strokeSamples) {
        return new VampiricRitualPointState(
                id,
                new Vector3d(0d, 0.15d, 0d),
                false,
                0.52d,
                "fang_wake",
                "fang_wake",
                1,
                4,
                true,
                List.of(new Vector3d(0d, 0.15d, 0d), new Vector3d(0.2d, 0.15d, 0d)),
                java.util.stream.IntStream.range(0, strokeSamples)
                        .mapToObj(index -> new Vector3d(index * 0.05d, 0.15d, 0d))
                        .toList());
    }
}
