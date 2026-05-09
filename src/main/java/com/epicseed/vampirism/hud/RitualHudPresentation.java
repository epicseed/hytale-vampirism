package com.epicseed.vampirism.hud;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;

final class RitualHudPresentation {

    private RitualHudPresentation() {
    }

    @Nonnull
    static DisplayState present(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                @Nonnull RitualHudDisplayMode displayMode) {
        if (snapshot == null) {
            return DisplayState.hidden();
        }
        TraceState traceState = traceState(snapshot);
        return new DisplayState(
                true,
                snapshot.displayName(),
                formatPhase(snapshot.phase()),
                guidance(snapshot, traceState),
                progress(snapshot),
                context(snapshot, traceState),
                stability(snapshot.stability()),
                corruption(snapshot.corruption()),
                expandedVisible(snapshot, displayMode, traceState));
    }

    private static boolean expandedVisible(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
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
    private static String progress(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
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
    private static String context(@Nonnull VampiricRitualRuntimeSnapshot snapshot, @Nullable TraceState traceState) {
        if (traceState != null) {
            return "Primary traces " + traceState.symbolName()
                    + " · release to stop · "
                    + traceState.traceProgress()
                    + " / " + traceState.totalTraceSteps();
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
            case SUCCESS -> "The ritual settled. Reveal it again if you need one more pulse.";
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
                        @Nonnull String context,
                        @Nonnull String stability,
                        @Nonnull String corruption,
                        boolean expandedVisible) {

        @Nonnull
        static DisplayState hidden() {
            return new DisplayState(false, "", "", "", "", "", "", "", false);
        }
    }

    private record TraceState(@Nonnull String symbolName, int traceProgress, int totalTraceSteps) {
    }
}
