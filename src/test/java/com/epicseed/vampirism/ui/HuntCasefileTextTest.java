package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.vampirism.domain.player.NamedHuntProgress;
import com.epicseed.epiccore.vampirism.domain.player.NightHuntCasefileProgress;
import com.epicseed.epiccore.vampirism.domain.player.PersistedNightHuntState;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;

class HuntCasefileTextTest {

    @BeforeAll
    static void setUp() {
        NightHuntSpawnRegistry.init();
    }

    @Test
    void resolveMentionsFollowUpSourceWhenCasefileComesFromLastClear() {
        PersistedNightHuntState persisted = new PersistedNightHuntState();
        persisted.casefileDisplayName = "Ledger Echo";
        persisted.casefileStage = "active";
        persisted.casefileEnvironmentId = "ledger-crossroads";
        persisted.casefileEncounterBeatId = "ledger-scrutiny";
        persisted.casefileRouteEventId = "lantern-shadowing";

        NamedHuntProgress progress = new NamedHuntProgress();
        progress.hunterCasefile = new NightHuntCasefileProgress();
        progress.hunterCasefile.casefileId = "ledger-echo";
        progress.hunterCasefile.stage = "active";
        progress.hunterCasefile.lastClearedCasefileId = "hunter-watch";
        progress.hunterCasefile.lastClearedCasefileClearedAtMs = 8_000L;

        HuntCasefileText.View view = HuntCasefileText.resolve(persisted, progress);

        assertTrue(view.detail().contains("Opened off the last cleared Hunter Watch."));
        assertTrue(view.detail().contains("Ledger Crossroads"));
        assertTrue(view.detail().contains("Lantern Shadowing"));
    }
}
