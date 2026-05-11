package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.skill.ui.ProgressionCardView;
import com.epicseed.epiccore.vampirism.domain.player.MasqueradeHeatState;
import com.epicseed.epiccore.vampirism.domain.player.NamedHuntProgress;
import com.epicseed.epiccore.vampirism.domain.player.PersistedNightHuntState;
import com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;
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
                masterySnapshot(0, 35L, "ancient"),
                snapshot,
                MasqueradeHeatPolicy.defaults(),
                "elder",
                lineageEvaluation("voidspawn", "Voidspawn", 25.0d, false, List.of("Heat gated")),
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
                        "drained"),
                crackdownState(2, 30f, 50_000L),
                new NamedHuntProgress());

        assertEquals("Watched · 30.0 heat", card(cards, "Current Exposure").value());
        assertEquals("Hunted at 45.0", card(cards, "Next Threshold").value());
        assertTrue(card(cards, "Next Threshold").detail().contains("15.0 heat remaining"));
        assertEquals("Pressure 45", card(cards, "Current Risk").value());
        assertEquals("Hunter crackdown · Siphon Ledger II", card(cards, "Next Hunt Window").value());
        assertTrue(card(cards, "Next Hunt Window").detail().contains("delayed +30s"));
        assertEquals("Elder · Voidspawn", card(cards, "Identity Pressure").value());
        assertTrue(card(cards, "Identity Pressure").detail().contains("visibility +20%"));
        assertTrue(card(cards, "Identity Pressure").detail().contains("threat escalation -20%"));
        assertTrue(card(cards, "Identity Pressure").detail().contains("bends live adaptation toward route counterplay"));
        assertEquals("Hunter crackdown · Siphon Ledger II", card(cards, "Pressure Outlook").value());
        assertTrue(card(cards, "Pressure Outlook").detail().contains("Break this chain before the next hunt"));
        assertTrue(card(cards, "Pressure Outlook").detail().contains("Voidspawn will bend the next live adaptation toward route counterplay."));
        assertTrue(card(cards, "Pressure Outlook").detail().contains("Last pressure payoff: Pressure resonance +35 age toward Ancient"));
        assertEquals("Siphon Ledger II", card(cards, "Pressure Drivers").value());
        assertTrue(card(cards, "Pressure Drivers").detail().contains("keeping Hunter crackdown live"));
        assertTrue(card(cards, "Pressure Drivers").detail().contains("Voidspawn will bend the next live adaptation toward route counterplay."));
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
                emptyMastery(),
                snapshot,
                MasqueradeHeatPolicy.defaults(),
                "fledgling",
                lineageEvaluation("voidspawn", "Voidspawn", 25.0d, true, List.of()),
                Map.of(),
                List.of(lineageEvaluation("voidspawn", "Voidspawn", 25.0d, true, List.of())),
                NightHuntContinuitySnapshot.empty(),
                new PersistedNightHuntState(),
                new NamedHuntProgress());

        assertEquals("Routes clear", card(cards, "Current Risk").value());
        assertEquals("Routes open", card(cards, "Next Hunt Window").value());
        assertEquals("Fledgling · Voidspawn", card(cards, "Identity Pressure").value());
        assertEquals("Quiet routes", card(cards, "Pressure Outlook").value());
        assertTrue(card(cards, "Pressure Outlook").detail().contains("No active world response"));
        assertTrue(card(cards, "Pressure Outlook").detail().contains("Voidspawn will bend the next live adaptation toward route counterplay."));
        assertEquals("No active driver", card(cards, "Pressure Drivers").value());
        assertTrue(card(cards, "Pressure Drivers").detail().contains("No chain, threat, or memory"));
        assertTrue(card(cards, "Pressure Drivers").detail().contains("Voidspawn will bend the next live adaptation toward route counterplay."));
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
                emptyMastery(),
                snapshot,
                MasqueradeHeatPolicy.defaults(),
                "fledgling",
                null,
                Map.of("void", 1),
                List.of(lineageEvaluation(
                        "voidspawn",
                        "Voidspawn",
                        25.0d,
                        false,
                        List.of(new VampiricRitualDefinition.AffinityRequirement("void", 2)),
                        List.of("missing_affinity:void:2"))),
                NightHuntContinuitySnapshot.empty(),
                new PersistedNightHuntState(),
                new NamedHuntProgress());

        assertEquals("Fledgling · Unbound", card(cards, "Identity Pressure").value());
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

    @Test
    void summarizePreparationDetailIncludesAffinityLaneBonus() {
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
                "Siphon Contract · Vermin focus · +15 mastery · +1 blood on matching Vermin hunts"
                        + " · Signature pull: Siphon Warren Signature · Blooded Warren → Drain Brood Spiral"
                        + " · +8 mastery · +1 blood · Affinity Vermin +1 when resonance lands the full pairing"
                        + " · Shorter routes and sustained siphon pressure turn Vermin hunts into the steady affinity farming lane. Open the Hunt Briefing to preview and change.",
                VampirismSkillTreeUiAdapter.summarizePreparationDetail(loadout));
    }

    @Test
    void summarizeRecentHuntRewardIncludesPreparationLaneReadout() {
        NightHuntMasterySnapshot mastery = new NightHuntMasterySnapshot(
                120,
                2,
                2,
                Map.of(),
                Map.of("monstrous", 1),
                java.util.Set.of("elderfang"),
                0,
                new NightHuntProgressionRegistry.RankDefinition("blooded", "Blooded", 0, 1, "#b91c1c"),
                null,
                0,
                "night-hunt:dread:elderfang",
                "elderfang",
                null,
                null,
                "monstrous",
                1,
                100,
                0,
                0L,
                "monstrous",
                1,
                "dread-mantle",
                "monstrous",
                20,
                0,
                10,
                0,
                0,
                0L,
                null,
                1L);

        assertEquals(
                "+1 skill · +100 mastery · Monstrous +1 · Preparation bonus: Dread Mantle · Monstrous focus · +20 mastery on matching Monstrous hunts"
                        + " · Resonance +10 mastery on owned Monstrous hunts"
                        + " · Heavier omen pressure and a stronger trail tier keep Monstrous hunts on the fear-driven specialization lane.",
                VampirismSkillTreeUiAdapter.summarizeRecentHuntReward(mastery));
    }

    private static ProgressionCardView card(List<ProgressionCardView> cards, String title) {
        return cards.stream()
                .filter(card -> title.equals(card.title()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing card: " + title));
    }

    private static NightHuntMasterySnapshot emptyMastery() {
        return masterySnapshot(0, 0L, null);
    }

    private static NightHuntMasterySnapshot masterySnapshot(int pressureResonanceMasteryBonus,
                                                            long pressureResonanceAgeProgress,
                                                            String pressureResonanceTargetAgeTierId) {
        return new NightHuntMasterySnapshot(
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                java.util.Set.of(),
                0,
                new NightHuntProgressionRegistry.RankDefinition("blooded", "Blooded", 0, 1, "#b91c1c"),
                null,
                0,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                0,
                0L,
                null,
                0,
                null,
                null,
                0,
                0,
                0,
                0,
                pressureResonanceMasteryBonus,
                pressureResonanceAgeProgress,
                pressureResonanceTargetAgeTierId,
                0L);
    }

    private static PersistedNightHuntState crackdownState(int tier, float extraCooldownSeconds, long cooldownEndsAtMs) {
        PersistedNightHuntState persisted = new PersistedNightHuntState();
        persisted.crackdownTier = tier;
        persisted.crackdownExtraCooldownSeconds = extraCooldownSeconds;
        persisted.cooldownEndsAtMs = System.currentTimeMillis() + cooldownEndsAtMs;
        persisted.worldThreatName = tier >= 2 ? "Hunter crackdown" : "Hunter watch";
        return persisted;
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
