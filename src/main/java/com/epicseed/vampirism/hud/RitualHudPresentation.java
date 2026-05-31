package com.epicseed.vampirism.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTraceProgress;

final class RitualHudPresentation {

    private static final String COLOR_OK = "#4f7a5c";
    private static final String COLOR_WARN = "#b87b36";
    private static final String COLOR_DANGER = "#9b3038";
    private static final String COLOR_MUTED = "#9aa3af";

    private RitualHudPresentation() {
    }

    @Nonnull
    static DisplayState present(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                @Nonnull RitualHudDisplayMode displayMode) {
        if (snapshot == null) {
            return DisplayState.hidden();
        }
        TraceState traceState = traceState(snapshot);
        boolean detailVisible = detailVisible(snapshot, displayMode, traceState);
        List<ChecklistRow> checklistRows = checklistRows(snapshot, displayMode, traceState, detailVisible);
        return new DisplayState(
                true,
                snapshot.displayName(),
                formatPhase(snapshot.phase()),
                guidance(snapshot, traceState),
                progress(snapshot, traceState, displayMode),
                progressColor(snapshot, displayMode),
                context(snapshot, traceState),
                stability(snapshot.stability()),
                corruption(snapshot.corruption()),
                checklistRows,
                displayMode == RitualHudDisplayMode.EXPANDED,
                detailVisible);
    }

    private static boolean detailVisible(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                         @Nonnull RitualHudDisplayMode displayMode,
                                         @Nullable TraceState traceState) {
        return switch (displayMode) {
            case EXPANDED -> true;
            case CONTEXTUAL -> snapshot.phase() == VampiricRitualRuntimePhase.PREPARING
                    || snapshot.phase() == VampiricRitualRuntimePhase.BINDING
                    || traceState != null
                    || snapshot.interferenceCount() > 0
                    || snapshot.phase() == VampiricRitualRuntimePhase.CHANNELING
                    || snapshot.phase() == VampiricRitualRuntimePhase.SUCCESS
                    || snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE
                    || snapshot.phase() == VampiricRitualRuntimePhase.COLLAPSE;
            case MINIMAL -> false;
        };
    }

    @Nonnull
    private static String guidance(@Nonnull VampiricRitualRuntimeSnapshot snapshot, @Nullable TraceState traceState) {
        if (traceState != null) {
            return "Trace " + traceState.symbolName();
        }
        return switch (snapshot.phase()) {
            case PREPARING -> snapshot.complete() ? "Commit the circle" : "Trace remaining sigils";
            case BINDING -> "Circle committed";
            case CHANNELING -> "Ritual resolving";
            case UNSTABLE -> snapshot.interferenceCount() > 0 ? "Clear interference" : "Steady the circle";
            case SUCCESS -> "Ritual settled";
            case COLLAPSE -> "Circle collapsed";
        };
    }

    @Nonnull
    private static String progress(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                   @Nullable TraceState traceState,
                                   @Nonnull RitualHudDisplayMode displayMode) {
        if (displayMode != RitualHudDisplayMode.EXPANDED) {
            String compactLiveStatus = compactLiveStatus(snapshot.liveStatus());
            if (!compactLiveStatus.isBlank()) {
                return compactLiveStatus;
            }
        }
        if (traceState != null) {
            return "Trace " + traceState.progressText();
        }
        if (snapshot.requiredChannelSeconds() > 0d
                && (snapshot.phase() == VampiricRitualRuntimePhase.CHANNELING
                || snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE)) {
            return String.format(
                    Locale.ROOT,
                    "Channel %.1fs / %.1fs",
                    Math.max(0d, snapshot.channelProgressSeconds()),
                    snapshot.requiredChannelSeconds());
        }
        if (snapshot.totalPoints() <= 0) {
            return "";
        }
        return "Sigils " + snapshot.activatedPoints() + " / " + snapshot.totalPoints();
    }

    @Nonnull
    private static String progressColor(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                        @Nonnull RitualHudDisplayMode displayMode) {
        VampiricRitualRuntimeSnapshot.LiveStatus liveStatus = snapshot.liveStatus();
        if (displayMode == RitualHudDisplayMode.EXPANDED || liveStatus == null) {
            return COLOR_MUTED;
        }
        return riskColor(Math.max(timeRisk(liveStatus), distanceRisk(liveStatus)));
    }

    @Nonnull
    private static String compactLiveStatus(@Nullable VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        if (liveStatus == null) {
            return "";
        }
        List<String> parts = new ArrayList<>(2);
        if (liveStatus.timeoutEnabled()) {
            parts.add(formatCompactSeconds(liveStatus.timeoutRemainingSeconds()));
        }
        if (liveStatus.distanceLimitEnabled()) {
            parts.add(formatMeters(liveStatus.distanceFromAnchor()));
        }
        return String.join(" · ", parts);
    }

    @Nonnull
    private static String context(@Nonnull VampiricRitualRuntimeSnapshot snapshot, @Nullable TraceState traceState) {
        if (traceState != null) {
            return "Primary traces " + traceState.symbolName()
                    + " · release to stop · "
                    + traceState.progressText();
        }
        if (snapshot.interferenceCount() > 0) {
            return snapshot.interferenceCount() == 1
                    ? "An interference is bleeding into the circle."
                    : snapshot.interferenceCount() + " interferences are bleeding into the circle.";
        }
        return switch (snapshot.phase()) {
            case PREPARING -> snapshot.complete()
                    ? "Circle ready. Press Ability3 beside the coffin to commit it."
                    : "Primary traces sigils. Secondary clears the circle.";
            case BINDING -> "The committed circle is taking hold. Stay near the coffin.";
            case CHANNELING -> "The ritual is resolving. Stay near the coffin until it settles.";
            case UNSTABLE -> "The circle is slipping. Recover stability before it collapses.";
            case SUCCESS -> "The ritual settled. Its afterimage lingers briefly, then fades on its own.";
            case COLLAPSE -> "The circle collapsed. Retrace the sigils to try again.";
        };
    }

    @Nonnull
    private static String stability(double value) {
        long rounded = roundedPercent(value);
        if (rounded >= 90) {
            return "Stability steady · " + rounded + "%";
        }
        if (rounded >= 70) {
            return "Stability holding · " + rounded + "%";
        }
        if (rounded >= 45) {
            return "Stability wavering · " + rounded + "%";
        }
        if (rounded >= 20) {
            return "Stability failing · " + rounded + "%";
        }
        return "Stability critical · " + rounded + "%";
    }

    @Nonnull
    private static String corruption(double value) {
        long rounded = roundedPercent(value);
        if (rounded <= 10) {
            return "Corruption calm · " + rounded + "%";
        }
        if (rounded <= 30) {
            return "Corruption low · " + rounded + "%";
        }
        if (rounded <= 55) {
            return "Corruption rising · " + rounded + "%";
        }
        if (rounded <= 80) {
            return "Corruption severe · " + rounded + "%";
        }
        return "Corruption overwhelming · " + rounded + "%";
    }

    private static long roundedPercent(double value) {
        return Math.max(0L, Math.round(value));
    }

    @Nonnull
    private static List<ChecklistRow> checklistRows(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                                    @Nonnull RitualHudDisplayMode displayMode,
                                                    @Nullable TraceState traceState,
                                                    boolean detailVisible) {
        if (!detailVisible) {
            return List.of();
        }
        if (displayMode == RitualHudDisplayMode.EXPANDED) {
            return expandedChecklistRows(snapshot);
        }
        return contextualChecklistRows(snapshot, traceState);
    }

    @Nonnull
    private static List<ChecklistRow> contextualChecklistRows(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                                             @Nullable TraceState traceState) {
        List<ChecklistRow> rows = new ArrayList<>();
        rows.add(traceState != null ? traceRow(traceState) : sigilsRow(snapshot));
        rows.add(phaseRow(snapshot));
        VampiricRitualRuntimeSnapshot.LiveStatus liveStatus = snapshot.liveStatus();
        if (liveStatus != null) {
            if (liveStatus.timeoutEnabled()) {
                rows.add(timeoutRow(liveStatus));
            }
            if (liveStatus.distanceLimitEnabled()) {
                rows.add(distanceRow(liveStatus));
            }
        }
        rows.add(safetyRow(snapshot));
        return List.copyOf(rows);
    }

    @Nonnull
    private static List<ChecklistRow> expandedChecklistRows(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        List<ChecklistRow> rows = new ArrayList<>();
        rows.add(phaseRow(snapshot).expanded());
        rows.add(sigilsRow(snapshot).expanded());
        if (snapshot.requiredChannelSeconds() > 0d
                || snapshot.phase() == VampiricRitualRuntimePhase.CHANNELING
                || snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE) {
            rows.add(channelRow(snapshot));
        }
        VampiricRitualRuntimeSnapshot.LiveStatus liveStatus = snapshot.liveStatus();
        if (liveStatus != null) {
            appendLiveStatusRows(rows, liveStatus);
        }
        rows.add(stabilityRow(snapshot));
        rows.add(corruptionRow(snapshot));
        rows.add(interferenceRow(snapshot));
        return List.copyOf(rows);
    }

    @Nonnull
    private static ChecklistRow traceRow(@Nonnull TraceState traceState) {
        return new ChecklistRow(
                "RUN",
                "Trace",
                traceState.symbolName() + " · " + traceState.progressText(),
                COLOR_WARN,
                traceState.traceProgress(),
                traceState.totalTraceSteps(),
                20);
    }

    @Nonnull
    private static ChecklistRow sigilsRow(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        boolean complete = snapshot.complete();
        return new ChecklistRow(
                complete ? "OK" : "DO",
                "Sigils",
                snapshot.activatedPoints() + " / " + snapshot.totalPoints(),
                complete ? COLOR_OK : COLOR_WARN,
                snapshot.activatedPoints(),
                Math.max(1, snapshot.totalPoints()),
                20);
    }

    private static void appendLiveStatusRows(@Nonnull List<ChecklistRow> rows,
                                             @Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        if (liveStatus.timeoutEnabled()) {
            rows.add(timeoutRow(liveStatus));
        }
        if (liveStatus.distanceLimitEnabled()) {
            rows.add(distanceRow(liveStatus));
            if (liveStatus.outsideDistanceLimit() && liveStatus.distanceGraceEnabled()) {
                rows.add(distanceGraceRow(liveStatus));
            }
        }
        if (liveStatus.cancelIfAnchorInvalid() || !liveStatus.anchorValid()) {
            rows.add(anchorRow(liveStatus));
        }
        if (liveStatus.cancelOnUnequipTool() || !liveStatus.toolEquipped()) {
            rows.add(toolRow(liveStatus));
        }
        if (liveStatus.cancelOnOwnerDeath() || liveStatus.ownerDead()) {
            rows.add(ownerRow(liveStatus));
        }
    }

    @Nonnull
    private static ChecklistRow timeoutRow(@Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        double remaining = liveStatus.timeoutRemainingSeconds();
        int risk = timeRisk(liveStatus);
        return new ChecklistRow(
                riskMark(risk),
                "Time",
                formatSeconds(remaining) + " remaining",
                riskColor(risk),
                remaining,
                Math.max(0.1d, liveStatus.timeoutSeconds()),
                28);
    }

    @Nonnull
    private static ChecklistRow distanceRow(@Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        int risk = distanceRisk(liveStatus);
        return new ChecklistRow(
                riskMark(risk),
                "Distance",
                formatMeters(liveStatus.distanceFromAnchor()) + " / " + formatMeters(liveStatus.maxDistanceFromAnchor()),
                riskColor(risk),
                Math.min(liveStatus.distanceFromAnchor(), liveStatus.maxDistanceFromAnchor()),
                Math.max(0.1d, liveStatus.maxDistanceFromAnchor()),
                28);
    }

    @Nonnull
    private static ChecklistRow distanceGraceRow(@Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        return new ChecklistRow(
                "!",
                "Grace",
                formatSeconds(liveStatus.distanceGraceRemainingSeconds()) + " before break",
                COLOR_DANGER,
                liveStatus.distanceGraceRemainingSeconds(),
                Math.max(0.1d, liveStatus.distanceGraceSeconds()),
                28);
    }

    @Nonnull
    private static ChecklistRow anchorRow(@Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        return new ChecklistRow(
                liveStatus.anchorValid() ? "OK" : "!",
                "Anchor",
                liveStatus.anchorValid() ? "Anchor intact" : "Anchor invalid",
                liveStatus.anchorValid() ? COLOR_OK : COLOR_DANGER,
                0,
                0,
                28);
    }

    @Nonnull
    private static ChecklistRow toolRow(@Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        return new ChecklistRow(
                liveStatus.toolEquipped() ? "OK" : "!",
                "Tool",
                liveStatus.toolEquipped() ? "Ritual tool held" : "Hold the ritual tool",
                liveStatus.toolEquipped() ? COLOR_OK : COLOR_DANGER,
                0,
                0,
                28);
    }

    @Nonnull
    private static ChecklistRow ownerRow(@Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        return new ChecklistRow(
                liveStatus.ownerDead() ? "!" : "OK",
                "Caster",
                liveStatus.ownerDead() ? "Caster down" : "Caster standing",
                liveStatus.ownerDead() ? COLOR_DANGER : COLOR_OK,
                0,
                0,
                28);
    }

    @Nonnull
    private static ChecklistRow phaseRow(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        return switch (snapshot.phase()) {
            case PREPARING -> new ChecklistRow(
                    snapshot.complete() ? "OK" : "DO",
                    "Commit",
                    snapshot.complete() ? "Ready at anchor" : "Finish the circle",
                    snapshot.complete() ? COLOR_OK : COLOR_WARN,
                    0,
                    0,
                    20);
            case BINDING -> new ChecklistRow("RUN", "Anchor", "Circle taking hold", COLOR_WARN, 0, 0, 20);
            case CHANNELING -> new ChecklistRow(
                    "RUN",
                    "Channel",
                    channelProgressText(snapshot),
                    COLOR_WARN,
                    snapshot.channelProgressSeconds(),
                    Math.max(0.1d, snapshot.requiredChannelSeconds()),
                    20);
            case UNSTABLE -> new ChecklistRow("!", "Recover", stability(snapshot.stability()), COLOR_DANGER, 0, 0, 20);
            case SUCCESS -> new ChecklistRow("OK", "Outcome", "Ritual settled", COLOR_OK, 0, 0, 20);
            case COLLAPSE -> new ChecklistRow("!", "Outcome", "Circle collapsed", COLOR_DANGER, 0, 0, 20);
        };
    }

    @Nonnull
    private static ChecklistRow safetyRow(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        if (snapshot.interferenceCount() > 0) {
            return new ChecklistRow(
                    "!",
                    "Clear",
                    snapshot.interferenceCount() == 1 ? "1 interference" : snapshot.interferenceCount() + " interferences",
                    COLOR_DANGER,
                    0,
                    0,
                    20);
        }
        if (snapshot.corruption() >= 55d) {
            return new ChecklistRow("!", "Corruption", corruption(snapshot.corruption()), COLOR_DANGER, 0, 0, 20);
        }
        if (snapshot.stability() < 70d) {
            return new ChecklistRow("DO", "Stability", stability(snapshot.stability()), COLOR_WARN, 0, 0, 20);
        }
        return new ChecklistRow("OK", "Stability", stability(snapshot.stability()), COLOR_OK, 0, 0, 20);
    }

    @Nonnull
    private static ChecklistRow channelRow(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        boolean complete = snapshot.requiredChannelSeconds() > 0d
                && snapshot.channelProgressSeconds() >= snapshot.requiredChannelSeconds();
        return new ChecklistRow(
                complete ? "OK" : "RUN",
                "Channel",
                channelProgressText(snapshot),
                complete ? COLOR_OK : COLOR_WARN,
                snapshot.channelProgressSeconds(),
                Math.max(0.1d, snapshot.requiredChannelSeconds()),
                28);
    }

    @Nonnull
    private static ChecklistRow stabilityRow(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        String mark = snapshot.stability() >= 70d ? "OK" : snapshot.stability() >= 45d ? "DO" : "!";
        String color = snapshot.stability() >= 70d ? COLOR_OK : snapshot.stability() >= 45d ? COLOR_WARN : COLOR_DANGER;
        return new ChecklistRow(
                mark,
                "Stability",
                stability(snapshot.stability()),
                color,
                snapshot.stability(),
                100,
                28);
    }

    @Nonnull
    private static ChecklistRow corruptionRow(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        String mark = snapshot.corruption() <= 30d ? "OK" : snapshot.corruption() <= 55d ? "DO" : "!";
        String color = snapshot.corruption() <= 30d ? COLOR_OK : snapshot.corruption() <= 55d ? COLOR_WARN : COLOR_DANGER;
        return new ChecklistRow(
                mark,
                "Corruption",
                corruption(snapshot.corruption()),
                color,
                100 - snapshot.corruption(),
                100,
                28);
    }

    @Nonnull
    private static ChecklistRow interferenceRow(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        if (snapshot.interferenceCount() > 0) {
            return new ChecklistRow(
                    "!",
                    "Interference",
                    snapshot.interferenceCount() == 1 ? "1 interference to clear" : snapshot.interferenceCount() + " interferences to clear",
                    COLOR_DANGER,
                    0,
                    0,
                    28);
        }
        return new ChecklistRow(
                "OK",
                "Interference",
                "Circle clear",
                COLOR_OK,
                0,
                0,
                28);
    }

    private static int timeRisk(@Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        if (!liveStatus.timeoutEnabled()) {
            return 0;
        }
        double remaining = liveStatus.timeoutRemainingSeconds();
        double ratio = liveStatus.timeoutSeconds() <= 0d ? 1d : remaining / liveStatus.timeoutSeconds();
        if (remaining <= 10d || ratio <= 0.10d) {
            return 2;
        }
        if (remaining <= 30d || ratio <= 0.25d) {
            return 1;
        }
        return 0;
    }

    private static int distanceRisk(@Nonnull VampiricRitualRuntimeSnapshot.LiveStatus liveStatus) {
        if (!liveStatus.distanceLimitEnabled()) {
            return 0;
        }
        if (liveStatus.outsideDistanceLimit()) {
            return 2;
        }
        double ratio = liveStatus.maxDistanceFromAnchor() <= 0d
                ? 0d
                : liveStatus.distanceFromAnchor() / liveStatus.maxDistanceFromAnchor();
        return ratio >= 0.75d ? 1 : 0;
    }

    @Nonnull
    private static String riskMark(int risk) {
        return risk >= 2 ? "!" : risk == 1 ? "DO" : "OK";
    }

    @Nonnull
    private static String riskColor(int risk) {
        return risk >= 2 ? COLOR_DANGER : risk == 1 ? COLOR_WARN : COLOR_OK;
    }

    @Nonnull
    private static String channelProgressText(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        if (snapshot.requiredChannelSeconds() <= 0d) {
            return "Hold the anchor";
        }
        return String.format(
                Locale.ROOT,
                "%.1fs / %.1fs",
                Math.max(0d, snapshot.channelProgressSeconds()),
                snapshot.requiredChannelSeconds());
    }

    @Nonnull
    private static String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0d, seconds));
    }

    @Nonnull
    private static String formatCompactSeconds(double seconds) {
        double safeSeconds = Math.max(0d, seconds);
        if (safeSeconds >= 10d) {
            return String.format(Locale.ROOT, "%.0fs", safeSeconds);
        }
        return formatSeconds(safeSeconds);
    }

    @Nonnull
    private static String formatMeters(double meters) {
        return String.format(Locale.ROOT, "%.1fm", Math.max(0d, meters));
    }

    @Nonnull
    private static String formatPhase(@Nonnull VampiricRitualRuntimePhase phase) {
        String name = phase.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    @Nullable
    private static TraceState traceState(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        for (VampiricRitualPointState pointState : snapshot.pointStates()) {
            if (pointState.tracing()) {
                return new TraceState(pointState.symbolName(), pointState.traceProgress(), pointState.totalTraceSteps());
            }
        }
        return null;
    }

    record DisplayState(boolean visible,
                        @Nonnull String title,
                        @Nonnull String phase,
                        @Nonnull String guidance,
                        @Nonnull String progress,
                        @Nonnull String progressColor,
                        @Nonnull String context,
                        @Nonnull String stability,
                        @Nonnull String corruption,
                        @Nonnull List<ChecklistRow> checklistRows,
                        boolean fullChecklistVisible,
                        boolean expandedVisible) {

        @Nonnull
        static DisplayState hidden() {
            return new DisplayState(false, "", "", "", "", COLOR_MUTED, "", "", "", List.of(), false, false);
        }
    }

    record ChecklistRow(@Nonnull String mark,
                        @Nonnull String title,
                        @Nonnull String detail,
                        @Nonnull String color,
                        double progressCurrent,
                        double progressTarget,
                        int height) {

        boolean hasProgress() {
            return progressTarget > 0d;
        }

        @Nonnull
        ChecklistRow expanded() {
            return new ChecklistRow(mark, title, detail, color, progressCurrent, progressTarget, Math.max(height, 28));
        }
    }

    private record TraceState(@Nonnull String symbolName,
                              @Nonnull String progressText,
                              int traceProgress,
                              int totalTraceSteps) {
        private TraceState(@Nonnull String symbolName, int traceProgress, int totalTraceSteps) {
            this(
                    symbolName,
                    VampiricRitualTraceProgress.displayProgressText(traceProgress, totalTraceSteps),
                    VampiricRitualTraceProgress.displayCurrentStep(traceProgress, totalTraceSteps),
                    totalTraceSteps);
        }
    }
}
