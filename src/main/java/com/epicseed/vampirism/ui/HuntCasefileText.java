package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.vampirism.domain.player.NamedHuntProgress;
import com.epicseed.epiccore.vampirism.domain.player.NightHuntCasefileProgress;
import com.epicseed.epiccore.vampirism.domain.player.PersistedNightHuntState;
import com.epicseed.vampirism.domain.hunt.NightHuntCasefileService;
import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;

final class HuntCasefileText {

    private HuntCasefileText() {
    }

    @Nonnull
    static View resolve(@Nonnull PersistedNightHuntState persisted,
                        @Nonnull NamedHuntProgress progress) {
        NightHuntCasefileProgress casefile = progress.hunterCasefile != null
                ? new NightHuntCasefileProgress(progress.hunterCasefile)
                : new NightHuntCasefileProgress();
        casefile.sanitize();
        String stage = persisted.casefileStage != null ? persisted.casefileStage : casefile.stage;
        if (!casefileActive(stage)) {
            return View.inactive();
        }
        String displayName = persisted.casefileDisplayName != null
                ? persisted.casefileDisplayName
                : humanize(persisted.casefileId != null ? persisted.casefileId : casefile.casefileId);
        String environmentId = persisted.casefileEnvironmentId != null
                ? persisted.casefileEnvironmentId
                : casefile.environmentId;
        String encounterBeatId = persisted.casefileEncounterBeatId != null
                ? persisted.casefileEncounterBeatId
                : casefile.encounterBeatId;
        String failStateId = persisted.casefileFailStateId != null
                ? persisted.casefileFailStateId
                : casefile.failStateId;
        String routeEventId = persisted.casefileRouteEventId != null
                ? persisted.casefileRouteEventId
                : casefile.routeEventId;
        String value = displayName + " · " + ("escalated".equals(stage) ? "Escalated" : "Active");
        String detail = "Biasing routes through " + humanize(environmentId)
                + " and " + humanize(encounterBeatId) + ".";
        String followUpSource = NightHuntCasefileService.followUpSourceDisplayName(progress);
        if (followUpSource != null) {
            detail = "Opened off the last cleared " + followUpSource + ". " + detail;
        }
        if (routeEventId != null) {
            detail = detail + " Route pressure leans toward " + humanize(routeEventId) + ".";
        }
        if ("escalated".equals(stage) && failStateId != null) {
            detail = detail + " Failure pressure now leans toward " + humanize(failStateId) + ".";
        }
        return new View(value, detail, true, "escalated".equals(stage));
    }

    private static boolean casefileActive(@Nullable String stage) {
        return NightHuntCasefileProgress.STAGE_ACTIVE.equals(stage)
                || NightHuntCasefileProgress.STAGE_ESCALATED.equals(stage);
    }

    @Nonnull
    private static String humanize(@Nullable String value) {
        return value == null || value.isBlank()
                ? "the current investigation"
                : NightHuntPresentationText.humanize(value);
    }

    record View(@Nonnull String value, @Nonnull String detail, boolean active, boolean escalated) {
        @Nonnull
        static View inactive() {
            return new View("", "", false, false);
        }
    }
}
