package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.player.MasqueradeHeatState;
import com.epicseed.vampirism.domain.lineage.VampiricClanDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.masquerade.MasqueradeExposureLevel;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;

class LineageWindowOpportunityTest {

    @Test
    void compactTextUsesReadyLineageWording() {
        LineageWindowOpportunity.View opportunity = LineageWindowOpportunity.resolve(
                new MasqueradeHeatSnapshot(
                        new MasqueradeHeatState(12.0d, 4_000L, 0),
                        0,
                        MasqueradeExposureLevel.QUIET,
                        false,
                        false),
                Map.of(),
                List.of(lineageEvaluation("voidspawn", "Voidspawn", 25.0d, true, List.of())));

        assertEquals(
                "Lineage window: Voidspawn ready - stay at or below 25.0 heat to keep it claimable.",
                opportunity.compactText());
    }

    @Test
    void compactTextUsesBlockedLineageWording() {
        LineageWindowOpportunity.View opportunity = LineageWindowOpportunity.resolve(
                new MasqueradeHeatSnapshot(
                        new MasqueradeHeatState(30.0d, 12_000L, 1),
                        45,
                        MasqueradeExposureLevel.WATCHED,
                        true,
                        false),
                Map.of(),
                List.of(lineageEvaluation("voidspawn", "Voidspawn", 25.0d, false, List.of("Heat gated"))));

        assertEquals(
                "Lineage window: Voidspawn blocked - cool 5.0 heat to reopen the 25.0 cap.",
                opportunity.compactText());
    }

    @Test
    void compactTextMentionsAffinityProgressWhenHeatWindowIsOpen() {
        LineageWindowOpportunity.View opportunity = LineageWindowOpportunity.resolve(
                new MasqueradeHeatSnapshot(
                        new MasqueradeHeatState(12.0d, 4_000L, 0),
                        0,
                        MasqueradeExposureLevel.QUIET,
                        false,
                        false),
                Map.of("void", 1),
                List.of(lineageEvaluation(
                        "voidspawn",
                        "Voidspawn",
                        25.0d,
                        false,
                        List.of(new VampiricRitualDefinition.AffinityRequirement("void", 2)),
                        List.of("missing_affinity:void:2"))));

        assertEquals(
                "Lineage window: Voidspawn pending - stay at or below 25.0 heat while you raise Void affinity to 2 (currently 1/2).",
                opportunity.compactText());
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
