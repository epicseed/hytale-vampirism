package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.skill.ui.ProgressionCardView;
import com.epicseed.epiccore.vampirism.domain.player.MasqueradeHeatState;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.domain.lineage.VampiricClanDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.masquerade.MasqueradeExposureLevel;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatPolicy;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;

class VampirismSkillTreeUiAdapterTest {

    @Test
    void buildHeatCardsShowsBlockedLineageOpportunityAndNextThreshold() {
        MasqueradeHeatSnapshot snapshot = new MasqueradeHeatSnapshot(
                new MasqueradeHeatState(30.0d, 12_000L, 1),
                45,
                MasqueradeExposureLevel.WATCHED,
                true,
                false);

        List<ProgressionCardView> cards = VampirismSkillTreeUiAdapter.buildHeatCards(
                snapshot,
                MasqueradeHeatPolicy.defaults(),
                Map.of(),
                List.of(lineageEvaluation("voidspawn", "Voidspawn", 25.0d, false, List.of("Heat gated"))),
                new NightHuntContinuitySnapshot(
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

        assertEquals("Watched · 30.0 heat", card(cards, "Current Exposure").value());
        assertEquals("Hunted at 45.0", card(cards, "Next Threshold").value());
        assertTrue(card(cards, "Next Threshold").detail().contains("15.0 heat remaining"));
        assertEquals("Pressure 45", card(cards, "Current Risk").value());
        assertEquals("Hunter crackdown · Siphon Ledger II", card(cards, "Pressure Outlook").value());
        assertTrue(card(cards, "Pressure Outlook").detail().contains("Break this chain before the next hunt"));
        assertEquals("Siphon Ledger II", card(cards, "Pressure Drivers").value());
        assertTrue(card(cards, "Pressure Drivers").detail().contains("keeping Hunter crackdown live"));
        assertEquals("Voidspawn blocked", card(cards, "Current Opportunity").value());
        assertTrue(card(cards, "Current Opportunity").detail().contains("Cool 5.0 heat"));
    }

    @Test
    void buildHeatCardsShowsWhenLowHeatWindowIsCurrentlyOpen() {
        MasqueradeHeatSnapshot snapshot = new MasqueradeHeatSnapshot(
                new MasqueradeHeatState(12.0d, 4_000L, 0),
                0,
                MasqueradeExposureLevel.QUIET,
                false,
                false);

        List<ProgressionCardView> cards = VampirismSkillTreeUiAdapter.buildHeatCards(
                snapshot,
                MasqueradeHeatPolicy.defaults(),
                Map.of(),
                List.of(lineageEvaluation("voidspawn", "Voidspawn", 25.0d, true, List.of())),
                NightHuntContinuitySnapshot.empty());

        assertEquals("Routes clear", card(cards, "Current Risk").value());
        assertEquals("Quiet routes", card(cards, "Pressure Outlook").value());
        assertTrue(card(cards, "Pressure Outlook").detail().contains("No active world response"));
        assertEquals("No active driver", card(cards, "Pressure Drivers").value());
        assertTrue(card(cards, "Pressure Drivers").detail().contains("No chain, threat, or memory"));
        assertEquals("Voidspawn ready", card(cards, "Current Opportunity").value());
        assertTrue(card(cards, "Current Opportunity").detail().contains("Stay at or below 25.0"));
    }

    @Test
    void buildHeatCardsShowsAffinityProgressWhenHeatSensitiveLineageIsOtherwiseReady() {
        MasqueradeHeatSnapshot snapshot = new MasqueradeHeatSnapshot(
                new MasqueradeHeatState(12.0d, 4_000L, 0),
                0,
                MasqueradeExposureLevel.QUIET,
                false,
                false);

        List<ProgressionCardView> cards = VampirismSkillTreeUiAdapter.buildHeatCards(
                snapshot,
                MasqueradeHeatPolicy.defaults(),
                Map.of("void", 1),
                List.of(lineageEvaluation(
                        "voidspawn",
                        "Voidspawn",
                        25.0d,
                        false,
                        List.of(new VampiricRitualDefinition.AffinityRequirement("void", 2)),
                        List.of("missing_affinity:void:2"))),
                NightHuntContinuitySnapshot.empty());

        assertEquals("Voidspawn pending", card(cards, "Current Opportunity").value());
        assertTrue(card(cards, "Current Opportunity").detail().contains("raise Void affinity to 2 (currently 1/2)"));
    }

    @Test
    void buildLineageCardsHumanizeAffinityBlockersAndLeadText() {
        List<ProgressionCardView> cards = VampirismSkillTreeUiAdapter.buildLineageCards(
                0L,
                2,
                Map.of("void", 1),
                List.of(lineageEvaluation(
                        "voidspawn",
                        "Voidspawn",
                        25.0d,
                        false,
                        List.of(new VampiricRitualDefinition.AffinityRequirement("void", 2)),
                        List.of("missing_affinity:void:2"))),
                null,
                0);

        assertTrue(card(cards, "Lineage Milestone").detail()
                .contains("Voidspawn needs you to raise Void affinity to 2 (currently 1/2)."));
        assertTrue(card(cards, "Voidspawn").detail().contains("Raise Void affinity to 2 (currently 1/2)"));
    }

    private static ProgressionCardView card(List<ProgressionCardView> cards, String title) {
        return cards.stream()
                .filter(card -> title.equals(card.title()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing card: " + title));
    }

    private static VampiricLineageEvaluation lineageEvaluation(String id,
                                                               String displayName,
                                                               double maxMasqueradeHeat,
                                                               boolean available,
                                                               List<String> blockingReasons) {
        return lineageEvaluation(id, displayName, maxMasqueradeHeat, available, List.of(), blockingReasons);
    }

    private static VampiricLineageEvaluation lineageEvaluation(String id,
                                                               String displayName,
                                                               double maxMasqueradeHeat,
                                                               boolean available,
                                                               List<VampiricRitualDefinition.AffinityRequirement> requiredAffinities,
                                                               List<String> blockingReasons) {
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
                        requiredAffinities,
                        maxMasqueradeHeat),
                List.of());
        return new VampiricLineageEvaluation(
                definition,
                new VampiricClanDefinition("voidcourt", "Void Court", "", "#8b5cf6"),
                false,
                available ? List.of() : blockingReasons);
    }
}
