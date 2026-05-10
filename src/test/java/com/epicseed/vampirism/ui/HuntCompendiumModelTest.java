package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierDefinition;
import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierSnapshot;

class HuntCompendiumModelTest {

    private static final VampiricAgeTierDefinition FLEDGLING =
            new VampiricAgeTierDefinition("fledgling", "Fledgling", "", 100L, "#c084fc");
    private static final VampiricAgeTierDefinition ELDER =
            new VampiricAgeTierDefinition("elder", "Elder", "", 250L, "#ef4444");

    @Test
    void overviewGuidanceTextPlacesLineageWindowUnderNextRite() {
        String text = HuntCompendiumModel.overviewGuidanceText(
                new HuntCompendiumNextRiteResolver.NextRite("Veil of Night", "Ready now. Return to a ritual anchor and invoke it."),
                new LineageWindowOpportunity.View(
                        "Voidspawn ready",
                        "Stay at or below 25.0 heat to keep it claimable.",
                        "#22c55e"),
                new VampiricAgeTierSnapshot(
                        "fledgling",
                        true,
                        FLEDGLING,
                        ELDER,
                        1,
                        3,
                        65L,
                        250L,
                        185L),
                "Next threshold: Watched at 20.0 - 8.0 heat remaining before hunter attention turns your way.");

        assertEquals(
                "\n\nNext rite: Veil of Night\nReady now. Return to a ritual anchor and invoke it.\n"
                        + "Lineage window: Voidspawn ready - stay at or below 25.0 heat to keep it claimable.\n"
                        + "Next rise: Elder - 185 progress remaining (65 / 250).\n"
                        + "Next threshold: Watched at 20.0 - 8.0 heat remaining before hunter attention turns your way.",
                text);
    }
}
