package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HuntCompendiumModelTest {

    @Test
    void overviewGuidanceTextPlacesLineageWindowUnderNextRite() {
        String text = HuntCompendiumModel.overviewGuidanceText(
                new HuntCompendiumNextRiteResolver.NextRite("Veil of Night", "Ready now. Return to a ritual anchor and invoke it."),
                new LineageWindowOpportunity.View(
                        "Voidspawn ready",
                        "Stay at or below 25.0 heat to keep it claimable.",
                        "#22c55e"));

        assertEquals(
                "\n\nNext rite: Veil of Night\nReady now. Return to a ritual anchor and invoke it.\n"
                        + "Lineage window: Voidspawn ready - stay at or below 25.0 heat to keep it claimable.",
                text);
    }
}
