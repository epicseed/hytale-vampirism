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
        return switch (snapshot.phase()) {
            case PREPARING -> snapshot.complete() ? "The circle is ready" : "Prepare the remaining glyphs";
            case BINDING -> traceState != null ? "Trace " + traceState.symbolName() : "Bind the circle";
            case CHANNELING -> "Maintain the channel";
            case UNSTABLE -> snapshot.interferenceCount() > 0 ? "Clear interference" : "Stabilize the ritual";
            case SUCCESS -> "Ritual complete";
            case COLLAPSE -> "Ritual collapsed";
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
        return "Glyphs " + snapshot.activatedPoints() + " / " + snapshot.totalPoints();
    }

    @Nonnull
    private static String context(@Nonnull VampiricRitualRuntimeSnapshot snapshot, @Nullable TraceState traceState) {
        if (traceState != null) {
            return "Next glyph: " + traceState.symbolName()
                    + " · " + traceState.traceProgress()
                    + " / " + traceState.totalTraceSteps();
        }
        if (snapshot.interferenceCount() > 0) {
            return snapshot.interferenceCount() == 1
                    ? "An interference is bleeding into the circle."
                    : snapshot.interferenceCount() + " interferences are bleeding into the circle.";
        }
        return switch (snapshot.phase()) {
            case PREPARING -> snapshot.complete()
                    ? "The circle is primed. Begin the binding when ready."
                    : "Wake each ritual point before binding.";
            case BINDING -> "Complete each glyph trace to seal the circle.";
            case CHANNELING -> "Stay within the circle until channeling ends.";
            case UNSTABLE -> "The circle is slipping. Steady it now.";
            case SUCCESS -> "The circle holds. Resolve the outcome.";
            case COLLAPSE -> "The seal broke. Rebuild the circle.";
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
