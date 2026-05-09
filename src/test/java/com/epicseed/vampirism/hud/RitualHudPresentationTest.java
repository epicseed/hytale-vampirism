package com.epicseed.vampirism.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

class RitualHudPresentationTest {

    @Test
    void hidesHudWhenSnapshotIsMissing() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(null, RitualHudDisplayMode.MINIMAL);

        assertFalse(state.visible());
        assertFalse(state.expandedVisible());
    }

    @Test
    void keepsDefaultPresentationMinimal() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(preparingSnapshot(), RitualHudDisplayMode.MINIMAL);

        assertTrue(state.visible());
        assertFalse(state.expandedVisible());
        assertEquals("Trace remaining sigils", state.guidance());
        assertEquals("Sigils 1 / 4", state.progress());
    }

    @Test
    void expandsContextuallyWhileTracing() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(tracingSnapshot(), RitualHudDisplayMode.CONTEXTUAL);

        assertTrue(state.expandedVisible());
        assertEquals("Trace Blood Sigil", state.guidance());
        assertTrue(state.context().contains("Primary traces Blood Sigil"));
        assertTrue(state.context().contains("release to stop"));
    }

    @Test
    void expandsContextuallyWhilePreparingWhenTheToolSurfaceRequestsIt() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(preparingSnapshot(), RitualHudDisplayMode.CONTEXTUAL);

        assertTrue(state.expandedVisible());
        assertTrue(state.context().contains("Primary traces sigils. Secondary clears the circle."));
    }

    @Test
    void expandedModeShowsDetailEvenWithoutImmediateContext() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(preparingSnapshot(), RitualHudDisplayMode.EXPANDED);

        assertTrue(state.expandedVisible());
        assertTrue(state.context().contains("Primary traces sigils. Secondary clears the circle."));
    }

    private static VampiricRitualRuntimeSnapshot preparingSnapshot() {
        return new VampiricRitualRuntimeSnapshot(
                "awakening",
                "Awakening Ritual",
                "Vampirism:ritual_anchor",
                new Vector3i(0, 64, 0),
                new Vector3d(0.5, 64.5, 0.5),
                VampiricRitualRuntimePhase.PREPARING,
                false,
                1,
                4,
                0,
                82,
                12,
                2,
                0,
                0,
                0,
                List.of(pointState(false, 0, 0)));
    }

    private static VampiricRitualRuntimeSnapshot tracingSnapshot() {
        return new VampiricRitualRuntimeSnapshot(
                "awakening",
                "Awakening Ritual",
                "Vampirism:ritual_anchor",
                new Vector3i(0, 64, 0),
                new Vector3d(0.5, 64.5, 0.5),
                VampiricRitualRuntimePhase.BINDING,
                true,
                2,
                4,
                0,
                68,
                28,
                2,
                0,
                0,
                0,
                List.of(pointState(true, 2, 5)));
    }

    private static VampiricRitualPointState pointState(boolean tracing, int traceProgress, int totalTraceSteps) {
        return new VampiricRitualPointState(
                "p0",
                new Vector3d(1.0, 64.0, 1.0),
                true,
                1.0,
                "blood_sigil",
                "Blood Sigil",
                traceProgress,
                totalTraceSteps,
                tracing,
                List.of(),
                List.of());
    }
}
