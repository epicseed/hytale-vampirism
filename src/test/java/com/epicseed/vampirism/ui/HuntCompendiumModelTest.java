package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierDefinition;
import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierSnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;

import java.util.Map;
import java.util.Set;

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

    @Test
    void preparationRecapTextSurfacesAffinityLaneBonus() {
        NightHuntPreparedLoadout loadout = new NightHuntPreparedLoadout(
                "bloodhound-rite",
                "Bloodhound Rite",
                "Sharpen the scent and commit to a direct blood pursuit.",
                "beast",
                20,
                0,
                "pursuit",
                "Blood Pursuit",
                "kill",
                "Bring the prey down before the scent fades.",
                1,
                0,
                1.0f,
                0,
                0f,
                0d);

        assertEquals(
                "Beast focus · +20 mastery on matching Beast hunts · Longer scent trails and a direct Blood Pursuit make Beast prey the cleanest specialization lane.",
                HuntCompendiumModel.preparationRecapText(loadout));
    }

    @Test
    void recentRewardLaneTextNamesMatchingPreparationLane() {
        NightHuntMasterySnapshot mastery = new NightHuntMasterySnapshot(
                120,
                2,
                2,
                Map.of(),
                Map.of("beast", 1),
                Set.of("emberwulf"),
                0,
                new NightHuntProgressionRegistry.RankDefinition("blooded", "Blooded", 0, 1, "#b91c1c"),
                null,
                0,
                "night-hunt:pursuit:emberwulf",
                "emberwulf",
                null,
                null,
                "beast",
                1,
                100,
                0,
                0L,
                "beast",
                1,
                "bloodhound-rite",
                "beast",
                20,
                0,
                1L);

        assertEquals(
                "Preparation bonus: Bloodhound Rite · Beast focus · +20 mastery on matching Beast hunts · Longer scent trails and a direct Blood Pursuit make Beast prey the cleanest specialization lane.",
                HuntCompendiumModel.recentRewardLaneText(mastery));
    }

    @Test
    void recentRewardLaneTextHighlightsHumanoidPreparationLane() {
        NightHuntMasterySnapshot mastery = new NightHuntMasterySnapshot(
                120,
                2,
                2,
                Map.of(),
                Map.of("humanoid", 1),
                Set.of("bandit"),
                0,
                new NightHuntProgressionRegistry.RankDefinition("blooded", "Blooded", 0, 1, "#b91c1c"),
                null,
                0,
                "night-hunt:pursuit:bandit",
                "bandit",
                null,
                null,
                "humanoid",
                1,
                100,
                0,
                0L,
                "humanoid",
                1,
                null,
                null,
                0,
                0,
                1L);

        assertEquals(
                "Matching lane: Shadow Pact · Humanoid focus · +15 mastery · +1 blood on matching Humanoid hunts"
                        + " · Closer routes and siphon pressure turn Humanoid hunts into the clean social-pressure lane.",
                HuntCompendiumModel.recentRewardLaneText(mastery));
    }
}
