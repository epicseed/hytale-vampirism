package com.epicseed.vampirism.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import org.joml.Vector3d;
import org.joml.Vector3i;

class RitualHudPresentationTest {

    @Test
    void hidesHudWhenSnapshotIsMissing() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(null, RitualHudDisplayMode.MINIMAL);

        assertFalse(state.visible());
        assertFalse(state.expandedVisible());
        assertTrue(state.checklistRows().isEmpty());
    }

    @Test
    void keepsDefaultPresentationMinimal() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(preparingSnapshot(), RitualHudDisplayMode.MINIMAL);

        assertTrue(state.visible());
        assertFalse(state.expandedVisible());
        assertEquals("Trace remaining sigils", state.guidance());
        assertEquals("Sigils 1 / 4", state.progress());
        assertTrue(state.checklistRows().isEmpty());
    }

    @Test
    void expandsContextuallyWhileTracing() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(tracingSnapshot(), RitualHudDisplayMode.CONTEXTUAL);

        assertTrue(state.expandedVisible());
        assertFalse(state.fullChecklistVisible());
        assertEquals("Trace Blood Sigil", state.guidance());
        assertEquals("Trace 3/5", state.progress());
        assertTrue(state.context().contains("Primary traces Blood Sigil"));
        assertTrue(state.context().contains("release to stop"));
        assertTrue(state.context().contains("3/5"));
        assertEquals("Trace", state.checklistRows().get(0).title());
        assertEquals("RUN", state.checklistRows().get(0).mark());
        assertEquals("Blood Sigil · 3/5", state.checklistRows().get(0).detail());
        assertTrue(state.checklistRows().get(0).hasProgress());
    }

    @Test
    void expandsContextuallyWhilePreparingWhenTheToolSurfaceRequestsIt() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(preparingSnapshot(), RitualHudDisplayMode.CONTEXTUAL);

        assertTrue(state.expandedVisible());
        assertFalse(state.fullChecklistVisible());
        assertTrue(state.context().contains("Primary traces sigils. Secondary clears the circle."));
        assertEquals("Stability", state.checklistRows().get(2).title());
        assertEquals("OK", state.checklistRows().get(2).mark());
    }

    @Test
    void expandedModeShowsDetailEvenWithoutImmediateContext() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(preparingSnapshot(), RitualHudDisplayMode.EXPANDED);

        assertTrue(state.expandedVisible());
        assertTrue(state.fullChecklistVisible());
        assertTrue(state.context().contains("Primary traces sigils. Secondary clears the circle."));
        assertTrue(state.checklistRows().size() > 3);
        assertEquals("Commit", state.checklistRows().get(0).title());
        assertEquals("Sigils", state.checklistRows().get(1).title());
        assertFalse(state.checklistRows().stream().anyMatch(row -> row.title().equals("Blood Sigil")));
    }

    @Test
    void expandedModeShowsLiveSafetyTelemetryWhenRuntimeProvidesIt() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(
                activeSnapshot(liveStatus(172.5, 5.25, false, 0, true, false, true)),
                RitualHudDisplayMode.EXPANDED);

        assertTrue(state.fullChecklistVisible());
        assertTrue(hasRow(state, "Sigils", "4 / 4"));
        assertTrue(hasRow(state, "Channel", "8.0s / 20.0s"));
        assertTrue(hasRow(state, "Time", "172.5s remaining"));
        assertTrue(hasRow(state, "Distance", "5.3m / 8.0m"));
        assertTrue(hasRow(state, "Anchor", "Anchor intact"));
        assertTrue(hasRow(state, "Tool", "Ritual tool held"));
        assertTrue(hasRow(state, "Caster", "Caster standing"));
    }

    @Test
    void expandedModeShowsDistanceGraceOnlyWhenOutsideTheLimit() {
        RitualHudPresentation.DisplayState inside = RitualHudPresentation.present(
                activeSnapshot(liveStatus(172.5, 5.25, false, 0, true, false, true)),
                RitualHudDisplayMode.EXPANDED);
        RitualHudPresentation.DisplayState outside = RitualHudPresentation.present(
                activeSnapshot(liveStatus(172.5, 9.4, true, 0.6, true, false, true)),
                RitualHudDisplayMode.EXPANDED);

        assertFalse(inside.checklistRows().stream().anyMatch(row -> row.title().equals("Grace")));
        assertTrue(hasRow(outside, "Distance", "9.4m / 8.0m"));
        assertTrue(hasRow(outside, "Grace", "0.6s before break"));
    }

    @Test
    void minimalModeUsesCompactLiveTimeAndDistanceWhenRuntimeProvidesIt() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(
                activeSnapshot(liveStatus(94.1, 3.4, false, 0, true, false, true)),
                RitualHudDisplayMode.MINIMAL);

        assertFalse(state.expandedVisible());
        assertEquals("94s · 3.4m", state.progress());
        assertEquals("#4f7a5c", state.progressColor());
        assertTrue(state.checklistRows().isEmpty());
    }

    @Test
    void contextualModeIncludesLiveTimeAndDistanceRows() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(
                activeSnapshot(liveStatus(20, 6.4, false, 0, true, false, true)),
                RitualHudDisplayMode.CONTEXTUAL);

        assertTrue(state.expandedVisible());
        assertFalse(state.fullChecklistVisible());
        assertEquals("20s · 6.4m", state.progress());
        assertEquals("#b87b36", state.progressColor());
        assertEquals("#b87b36", row(state, "Time").color());
        assertEquals("#b87b36", row(state, "Distance").color());
    }

    @Test
    void liveTimeAndDistanceRowsTurnDangerNearFailureThresholds() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(
                activeSnapshot(liveStatus(8, 8.4, true, 0.4, true, false, true)),
                RitualHudDisplayMode.EXPANDED);

        assertEquals("#9b3038", row(state, "Time").color());
        assertEquals("#9b3038", row(state, "Distance").color());
    }

    @Test
    void successCopyMatchesLingeringAfterimageBehavior() {
        RitualHudPresentation.DisplayState state = RitualHudPresentation.present(successSnapshot(), RitualHudDisplayMode.CONTEXTUAL);

        assertEquals("Ritual settled", state.guidance());
        assertEquals("The ritual settled. Its afterimage lingers briefly, then fades on its own.", state.context());
        assertEquals("Outcome", state.checklistRows().get(1).title());
        assertEquals("OK", state.checklistRows().get(1).mark());
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

    private static VampiricRitualRuntimeSnapshot successSnapshot() {
        return new VampiricRitualRuntimeSnapshot(
                "awakening",
                "Awakening Ritual",
                "Vampirism:ritual_anchor",
                new Vector3i(0, 64, 0),
                new Vector3d(0.5, 64.5, 0.5),
                VampiricRitualRuntimePhase.SUCCESS,
                false,
                4,
                4,
                0,
                100,
                10,
                0,
                0,
                0,
                0,
                List.of(pointState(false, 0, 0)));
    }

    private static VampiricRitualRuntimeSnapshot activeSnapshot(
            VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        return new VampiricRitualRuntimeSnapshot(
                "awakening",
                "Awakening Ritual",
                "Vampirism:ritual_anchor",
                new Vector3i(0, 64, 0),
                new Vector3d(0.5, 64.5, 0.5),
                null,
                null,
                VampiricRitualRuntimePhase.CHANNELING,
                true,
                4,
                4,
                92,
                76,
                18,
                2,
                0,
                8,
                20,
                List.of(),
                List.of(pointState(false, 0, 0)),
                liveStatus);
    }

    private static VampiricRitualRuntimeSnapshot.LiveStatus liveStatus(
            double timeoutRemainingSeconds,
            double distanceFromAnchor,
            boolean outsideDistanceLimit,
            double distanceGraceRemainingSeconds,
            boolean anchorValid,
            boolean ownerDead,
            boolean toolEquipped) {
        return new VampiricRitualRuntimeSnapshot.LiveStatus(
                true,
                timeoutRemainingSeconds,
                180,
                true,
                distanceFromAnchor,
                8,
                outsideDistanceLimit,
                true,
                distanceGraceRemainingSeconds,
                1,
                anchorValid,
                true,
                ownerDead,
                true,
                toolEquipped,
                true);
    }

    private static boolean hasRow(RitualHudPresentation.DisplayState state, String title, String detail) {
        return state.checklistRows().stream()
                .anyMatch(row -> row.title().equals(title) && row.detail().equals(detail));
    }

    private static RitualHudPresentation.ChecklistRow row(RitualHudPresentation.DisplayState state, String title) {
        return state.checklistRows().stream()
                .filter(checklistRow -> checklistRow.title().equals(title))
                .findFirst()
                .orElseThrow();
    }
}
