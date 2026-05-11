package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.domain.lineage.VampiricClanDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;

class PressureDriversTextTest {

    @Test
    void resolveShowsActiveChainAsPrimaryDriver() {
        PressureDriversText.View view = PressureDriversText.resolve(new NightHuntContinuitySnapshot(
                0,
                null,
                0,
                null,
                2,
                "Hunter crackdown",
                "Repeated siphon success drew notice",
                "Siphon Ledger",
                2,
                null,
                2,
                0,
                "drained"));

        assertEquals("Siphon Ledger II", view.value());
        assertTrue(view.detail().contains("keeping Hunter crackdown live"));
        assertEquals("#f97316", view.accentColor());
    }

    @Test
    void resolveShowsDominantMemoryBehindThreat() {
        PressureDriversText.View view = PressureDriversText.resolve(new NightHuntContinuitySnapshot(
                3,
                "Legend-haunted Emberwulf",
                1,
                "Read drain counterplay",
                2,
                "Hunter crackdown",
                null,
                null,
                0,
                null,
                2,
                0,
                "drained"));

        assertEquals("Legend-haunted Emberwulf", view.value());
        assertEquals(
                "That prey memory is feeding Hunter crackdown. Change prey or pace before the next hunt.",
                view.detail());
        assertEquals("#f97316", view.accentColor());
    }

    @Test
    void resolveShowsMemoryPressureEvenBeforeThreatStarts() {
        PressureDriversText.View view = PressureDriversText.resolve(new NightHuntContinuitySnapshot(
                1,
                "Wary Emberwulf",
                2,
                "Prepared drain counterplay",
                0,
                null,
                null,
                null,
                0,
                null,
                1,
                0,
                "drained"));

        assertEquals("Prepared drain counterplay", view.value());
        assertEquals(
                "That route memory is the strongest live tell. Change mode or resolution next hunt to keep pressure quiet.",
                view.detail());
        assertEquals("#f97316", view.accentColor());
    }

    @Test
    void resolveFallsBackToQuietWhenNoContinuityDriverIsActive() {
        PressureDriversText.View view = PressureDriversText.resolve(NightHuntContinuitySnapshot.empty());

        assertEquals("No active driver", view.value());
        assertTrue(view.detail().contains("No chain, threat, or memory"));
        assertEquals("#22c55e", view.accentColor());
    }

    @Test
    void resolveCallsOutLineageBiasOnDominantMemory() {
        PressureDriversText.View view = PressureDriversText.resolve(
                new NightHuntContinuitySnapshot(
                        1,
                        "Wary Emberwulf",
                        2,
                        "Prepared drain counterplay",
                        0,
                        null,
                        null,
                        null,
                        0,
                        null,
                        1,
                        0,
                        "drained"),
                lineageEvaluation("voidspawn", "Voidspawn"));

        assertEquals("Prepared drain counterplay", view.value());
        assertTrue(view.detail().contains("Voidspawn makes that route counterplay land +25% harder."));
    }

    private static VampiricLineageEvaluation lineageEvaluation(String id, String displayName) {
        VampiricLineageDefinition definition = new VampiricLineageDefinition(
                id,
                "voidcourt",
                displayName,
                displayName + " description",
                null,
                new VampiricLineageDefinition.UnlockRequirements(
                        null,
                        0,
                        java.util.Set.of(),
                        java.util.Set.of(),
                        java.util.List.of(),
                        25.0d),
                java.util.List.of());
        return new VampiricLineageEvaluation(
                definition,
                new VampiricClanDefinition("voidcourt", "Void Court", "", "#8b5cf6"),
                true,
                java.util.List.of());
    }
}
