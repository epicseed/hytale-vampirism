package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.domain.lineage.VampiricClanDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;

class PressureOutlookTextTest {

    @Test
    void resolveShowsEscalatedChainPressure() {
        PressureOutlookText.View view = PressureOutlookText.resolve(new NightHuntContinuitySnapshot(
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

        assertEquals("Hunter crackdown · Siphon Ledger II", view.value());
        assertTrue(view.detail().contains("Repeated siphon success drew notice"));
        assertEquals("#f97316", view.accentColor());
    }

    @Test
    void resolveShowsQuietFallbackWhenPressureIsInactive() {
        PressureOutlookText.View view = PressureOutlookText.resolve(NightHuntContinuitySnapshot.empty());

        assertEquals("Quiet routes", view.value());
        assertEquals(
                "No active world response is building right now. Keep the next hunt varied to hold that quiet.",
                view.detail());
        assertEquals("#22c55e", view.accentColor());
    }

    @Test
    void resolveCallsOutQuietButVisibleSuccessStreak() {
        PressureOutlookText.View view = PressureOutlookText.resolve(new NightHuntContinuitySnapshot(
                0,
                null,
                0,
                null,
                0,
                null,
                null,
                null,
                0,
                null,
                3,
                0,
                "drained"));

        assertEquals("Quiet routes", view.value());
        assertTrue(view.detail().contains("3-hunt streak"));
    }

    @Test
    void resolveQuietOutlookIncludesLineageAdaptationBias() {
        PressureOutlookText.View view = PressureOutlookText.resolve(
                NightHuntContinuitySnapshot.empty(),
                lineageEvaluation("voidspawn", "Voidspawn"));

        assertEquals("Quiet routes", view.value());
        assertTrue(view.detail().contains("Voidspawn will bend the next live adaptation toward route counterplay."));
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
