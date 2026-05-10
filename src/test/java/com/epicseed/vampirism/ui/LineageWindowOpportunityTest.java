package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.player.MasqueradeHeatState;
import com.epicseed.vampirism.domain.lineage.VampiricClanDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.masquerade.MasqueradeExposureLevel;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;

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
                List.of(lineageEvaluation("voidspawn", "Voidspawn", 25.0d, false, List.of("Heat gated"))));

        assertEquals(
                "Lineage window: Voidspawn blocked - cool 5.0 heat to reopen the 25.0 cap.",
                opportunity.compactText());
    }

    private static VampiricLineageEvaluation lineageEvaluation(String id,
                                                               String displayName,
                                                               double maxMasqueradeHeat,
                                                               boolean available,
                                                               List<String> blockingReasons) {
        VampiricLineageDefinition definition = new VampiricLineageDefinition(
                id,
                "voidcourt",
                displayName,
                displayName + " description",
                null,
                new VampiricLineageDefinition.UnlockRequirements(null, 0, java.util.Set.of(), java.util.Set.of(), maxMasqueradeHeat),
                List.of());
        return new VampiricLineageEvaluation(
                definition,
                new VampiricClanDefinition("voidcourt", "Void Court", "", "#8b5cf6"),
                false,
                available ? List.of() : blockingReasons);
    }
}
