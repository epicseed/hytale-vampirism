package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierDefinition;
import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierSnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;

import java.util.Map;
import java.util.Set;

class HuntCompendiumModelTest {

    private static final VampiricAgeTierDefinition FLEDGLING =
            new VampiricAgeTierDefinition("fledgling", "Fledgling", "", 100L, "#c084fc");
    private static final VampiricAgeTierDefinition ELDER =
            new VampiricAgeTierDefinition("elder", "Elder", "", 250L, "#ef4444");

    @BeforeAll
    static void setUp() {
        NightHuntProgressionRegistry.init();
        NightHuntSpawnRegistry.init();
    }

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
                "Beast focus · +20 mastery on matching Beast hunts"
                        + " · Signature pull: Bloodhound Mire Signature · Scent-Bound Mire → Blood Scent Relay"
                        + " · +10 mastery · +1 blood · Affinity Beast +1 when resonance lands the full pairing"
                        + " · Longer scent trails and a direct Blood Pursuit make Beast prey the cleanest specialization lane.",
                HuntCompendiumModel.preparationRecapText(loadout));
    }

    @Test
    void preparationRecapTextSurfacesVerminSignatureLaneBonus() {
        NightHuntPreparedLoadout loadout = new NightHuntPreparedLoadout(
                "siphon-rite",
                "Siphon Rite",
                "Shorten the route and prepare to finish the prey through draining pressure.",
                "vermin",
                15,
                1,
                "siphon",
                "Siphon Contract",
                "drain",
                "Wound the prey, then stay close until the rite drains it dry.",
                0,
                0,
                0.9f,
                2,
                6f,
                7d);

        assertEquals(
                "Vermin focus · +15 mastery · +1 blood on matching Vermin hunts"
                        + " · Signature pull: Siphon Warren Signature · Blooded Warren → Drain Brood Spiral"
                        + " · +8 mastery · +1 blood · Affinity Vermin +1 when resonance lands the full pairing"
                        + " · Shorter routes and sustained siphon pressure turn Vermin hunts into the steady affinity farming lane.",
                HuntCompendiumModel.preparationRecapText(loadout));
    }

    @Test
    void preparationRecapTextSurfacesHumanoidSignatureLaneBonus() {
        NightHuntPreparedLoadout loadout = new NightHuntPreparedLoadout(
                "shadow-pact",
                "Shadow Pact",
                "Turn the hunt intimate and keep close enough to unravel mortal resolve.",
                "humanoid",
                15,
                1,
                "siphon",
                "Siphon Contract",
                "drain",
                "Wound the prey, then stay close until the rite drains it dry.",
                -1,
                0,
                0.9f,
                2,
                6f,
                7d);

        assertEquals(
                "Humanoid focus · +15 mastery · +1 blood on matching Humanoid hunts"
                        + " · Signature pull: Shadow Court Signature · Whisper Court → Courtly Unraveling"
                        + " · +8 mastery · +10 age progress · Affinity Humanoid +1 when resonance lands the full pairing"
                        + " · Closer routes and siphon pressure turn Humanoid hunts into the clean social-pressure lane.",
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
                0,
                0,
                0,
                0L,
                null,
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
                0,
                0,
                0,
                0L,
                null,
                1L);

        assertEquals(
                "Matching lane: Shadow Pact · Humanoid focus · +15 mastery · +1 blood on matching Humanoid hunts"
                        + " · Closer routes and siphon pressure turn Humanoid hunts into the clean social-pressure lane.",
                HuntCompendiumModel.recentRewardLaneText(mastery));
    }

    @Test
    void recentRewardLaneTextHighlightsResonanceBonus() {
        NightHuntMasterySnapshot mastery = new NightHuntMasterySnapshot(
                170,
                3,
                2,
                Map.of(),
                Map.of("beast", 2),
                Set.of("wolf_black"),
                0,
                new NightHuntProgressionRegistry.RankDefinition("blooded", "Blooded", 0, 1, "#b91c1c"),
                null,
                0,
                "night-hunt:pursuit:wolf_black",
                "wolf_black",
                null,
                null,
                "beast",
                1,
                170,
                0,
                0L,
                "beast",
                0,
                "bloodhound-rite",
                "beast",
                20,
                0,
                10,
                0,
                0,
                0L,
                null,
                1L);

        assertEquals(
                "Preparation bonus: Bloodhound Rite · Beast focus · +20 mastery on matching Beast hunts"
                        + " · Resonance +10 mastery on owned Beast hunts"
                        + " · Longer scent trails and a direct Blood Pursuit make Beast prey the cleanest specialization lane.",
                HuntCompendiumModel.recentRewardLaneText(mastery));
    }

    @Test
    void recentRewardLaneTextHighlightsPressureResonanceBonus() {
        NightHuntMasterySnapshot mastery = new NightHuntMasterySnapshot(
                170,
                3,
                2,
                Map.of(),
                Map.of("humanoid", 2),
                Set.of("bandit"),
                0,
                new NightHuntProgressionRegistry.RankDefinition("blooded", "Blooded", 0, 1, "#b91c1c"),
                null,
                0,
                "night-hunt:siphon:bandit",
                "bandit",
                null,
                null,
                "humanoid",
                1,
                170,
                0,
                35L,
                "humanoid",
                0,
                "shadow-pact",
                "humanoid",
                15,
                1,
                10,
                0,
                0,
                35L,
                "ancient",
                1L);

        assertEquals(
                "Preparation bonus: Shadow Pact · Humanoid focus · +15 mastery · +1 blood on matching Humanoid hunts"
                        + " · Resonance +10 mastery on owned Humanoid hunts"
                        + " · Pressure resonance +35 age toward Ancient on elder lineage hunts that end in crackdown pressure"
                        + " · Closer routes and siphon pressure turn Humanoid hunts into the clean social-pressure lane.",
                HuntCompendiumModel.recentRewardLaneText(mastery));
    }

    @Test
    void recentRewardLaneTextHighlightsHumanoidSignatureRewardBundle() {
        NightHuntMasterySnapshot mastery = new NightHuntMasterySnapshot(
                173,
                4,
                2,
                Map.of(),
                Map.of("humanoid", 2),
                Set.of("goblin_thief"),
                0,
                new NightHuntProgressionRegistry.RankDefinition("blooded", "Blooded", 0, 1, "#b91c1c"),
                null,
                0,
                "night-hunt:siphon:goblin_thief",
                "goblin_thief",
                null,
                null,
                "humanoid",
                1,
                173,
                4,
                25L,
                "humanoid",
                1,
                "shadow-pact",
                "humanoid",
                15,
                1,
                10,
                0,
                0,
                0L,
                null,
                "shadow-court-signature",
                8,
                0,
                10L,
                "humanoid",
                1,
                1L);

        assertEquals(
                "Preparation bonus: Shadow Pact · Humanoid focus · +15 mastery · +1 blood on matching Humanoid hunts"
                        + " · Resonance +10 mastery on owned Humanoid hunts"
                        + " · Signature hunt: Shadow Court Signature · Whisper Court → Courtly Unraveling"
                        + " · +8 mastery · +10 age progress · Affinity Humanoid +1"
                        + " · Closer routes and siphon pressure turn Humanoid hunts into the clean social-pressure lane.",
                HuntCompendiumModel.recentRewardLaneText(mastery));
    }

    @Test
    void recentRewardLaneTextHighlightsSignatureRewardBundle() {
        NightHuntMasterySnapshot mastery = new NightHuntMasterySnapshot(
                180,
                4,
                2,
                Map.of(),
                Map.of("beast", 2),
                Set.of("wolf_black"),
                0,
                new NightHuntProgressionRegistry.RankDefinition("blooded", "Blooded", 0, 1, "#b91c1c"),
                null,
                0,
                "night-hunt:pursuit:wolf_black",
                "wolf_black",
                null,
                null,
                "beast",
                2,
                180,
                4,
                15L,
                "beast",
                1,
                "bloodhound-rite",
                "beast",
                20,
                0,
                10,
                0,
                0,
                0L,
                null,
                "bloodhound-mire-signature",
                10,
                1,
                0L,
                "beast",
                1,
                1L);

        assertEquals(
                "Preparation bonus: Bloodhound Rite · Beast focus · +20 mastery on matching Beast hunts"
                        + " · Resonance +10 mastery on owned Beast hunts"
                        + " · Signature hunt: Bloodhound Mire Signature · Scent-Bound Mire → Blood Scent Relay"
                        + " · +10 mastery · +1 blood · Affinity Beast +1"
                        + " · Longer scent trails and a direct Blood Pursuit make Beast prey the cleanest specialization lane.",
                HuntCompendiumModel.recentRewardLaneText(mastery));
    }
}
