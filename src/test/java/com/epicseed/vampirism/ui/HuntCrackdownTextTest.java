package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.player.NamedHuntProgress;
import com.epicseed.epiccore.vampirism.domain.player.NightHuntCasefileProgress;
import com.epicseed.epiccore.vampirism.domain.player.PersistedNightHuntState;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;

class HuntCrackdownTextTest {

    @BeforeAll
    static void setUp() {
        NightHuntSpawnRegistry.init();
    }

    @Test
    void resolveShowsActiveCrackdownDelay() {
        PersistedNightHuntState persisted = new PersistedNightHuntState();
        persisted.cooldownEndsAtMs = 40_000L;
        persisted.worldThreatName = "Hunter crackdown";
        persisted.crackdownTier = 2;
        persisted.crackdownExtraCooldownSeconds = 30f;
        persisted.casefileDisplayName = "Hunter Watch";
        persisted.casefileStage = "escalated";
        persisted.casefileEnvironmentId = "consecrated-threshold";
        persisted.casefileEncounterBeatId = "sacred-ground";
        persisted.casefileFailStateId = "blood-counter-ambush";
        persisted.casefileRouteEventId = "lantern-shadowing";

        NamedHuntProgress progress = new NamedHuntProgress();
        progress.hunterCasefile = new NightHuntCasefileProgress();
        progress.hunterCasefile.casefileId = "hunter-watch";
        progress.hunterCasefile.stage = "escalated";
        progress.hunterCasefile.routeEventId = "lantern-shadowing";

        HuntCrackdownText.View view = HuntCrackdownText.resolve(
                persisted,
                new NightHuntContinuitySnapshot(
                        2,
                        "Legend-haunted Emberwulf",
                        1,
                        "Prepared drain counterplay",
                        2,
                        "Hunter crackdown",
                        "Repeated siphon success drew notice",
                        "Siphon Ledger",
                        2,
                        "siphon:emberwulf",
                        2,
                        0,
                        "drained"),
                progress,
                1_000L);

        assertEquals("Hunter crackdown · Siphon Ledger II", view.value());
        assertTrue(view.detail().contains("delayed +30s"));
        assertTrue(view.detail().contains("reopen in 39s"));
        assertTrue(view.detail().contains("Hunter Watch · Escalated"));
        assertTrue(view.detail().contains("Lantern Shadowing"));
        assertEquals("#f97316", view.accentColor());
        assertTrue(view.active());
    }

    @Test
    void resolveShowsOpenRoutesWhenCooldownHasPassed() {
        PersistedNightHuntState persisted = new PersistedNightHuntState();
        persisted.crackdownTier = 1;
        persisted.crackdownExtraCooldownSeconds = 15f;

        HuntCrackdownText.View view = HuntCrackdownText.resolve(
                persisted,
                NightHuntContinuitySnapshot.empty(),
                new NamedHuntProgress(),
                20_000L);

        assertEquals("Routes open", view.value());
        assertEquals("No active crackdown is delaying the next hunt window.", view.detail());
    }

    @Test
    void resolveMentionsLastClearedCasefileWhenRoutesReopen() {
        PersistedNightHuntState persisted = new PersistedNightHuntState();

        NamedHuntProgress progress = new NamedHuntProgress();
        progress.hunterCasefile = new NightHuntCasefileProgress();
        progress.hunterCasefile.casefileId = "hunter-watch";
        progress.hunterCasefile.stage = "cleared";
        progress.hunterCasefile.clearedAtMs = 9_000L;

        HuntCrackdownText.View view = HuntCrackdownText.resolve(
                persisted,
                NightHuntContinuitySnapshot.empty(),
                progress,
                20_000L);

        assertEquals("Routes open", view.value());
        assertTrue(view.detail().contains("Hunter Watch"));
        assertTrue(view.detail().contains("pivoting away from an immediate repeat"));
    }
}
