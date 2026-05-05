package com.epicseed.vampirism.hud;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class RitualStatusHud extends CustomUIHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String ROOT = "#RitualHudRoot";
    private static final String TITLE = "#RitualName";
    private static final String PHASE = "#PhaseValue";
    private static final String STABILITY = "#StabilityValue";
    private static final String CORRUPTION = "#CorruptionValue";
    private static final String PROGRESS = "#ProgressValue";
    private static final String TRACE = "#TraceValue";

    private DisplayState state = DisplayState.hidden();

    public RitualStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append(VampirismUiPaths.ritualHudLayout());
        writeState(builder, state);
    }

    public void sync(@Nullable VampiricRitualRuntimeSnapshot snapshot) {
        DisplayState nextState = DisplayState.fromSnapshot(snapshot);
        if (state.equals(nextState)) {
            return;
        }
        state = nextState;
        pushState();
    }

    private void pushState() {
        try {
            UICommandBuilder builder = new UICommandBuilder();
            writeState(builder, state);
            update(false, builder);
        } catch (Exception e) {
            LOGGER.atSevere().log("[RitualStatusHud] Error updating HUD: " + e.getMessage());
        }
    }

    private static void writeState(@Nonnull UICommandBuilder builder, @Nonnull DisplayState state) {
        builder.set(ROOT + ".Visible", state.visible());
        builder.set(TITLE + ".Text", state.title());
        builder.set(PHASE + ".Text", state.phase());
        builder.set(STABILITY + ".Text", state.stability());
        builder.set(CORRUPTION + ".Text", state.corruption());
        builder.set(PROGRESS + ".Text", state.progress());
        builder.set(TRACE + ".Visible", state.traceVisible());
        builder.set(TRACE + ".Text", state.trace());
    }

    private record DisplayState(boolean visible,
                                @Nonnull String title,
                                @Nonnull String phase,
                                @Nonnull String stability,
                                @Nonnull String corruption,
                                @Nonnull String progress,
                                @Nonnull String trace,
                                boolean traceVisible) {

        @Nonnull
        private static DisplayState hidden() {
            return new DisplayState(false, "", "", "", "", "", "", false);
        }

        @Nonnull
        private static DisplayState fromSnapshot(@Nullable VampiricRitualRuntimeSnapshot snapshot) {
            if (snapshot == null) {
                return hidden();
            }
            TraceState traceState = traceState(snapshot);
            return new DisplayState(
                    true,
                    snapshot.displayName(),
                    "Phase: " + formatPhase(snapshot.phase()),
                    "Stability: " + formatRounded(snapshot.stability()),
                    "Corruption: " + formatRounded(snapshot.corruption()),
                    progressText(snapshot),
                    traceState.text(),
                    traceState.visible());
        }

        @Nonnull
        private static String progressText(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
            if (snapshot.phase().active() && snapshot.requiredChannelSeconds() > 0d) {
                return String.format(
                        Locale.ROOT,
                        "Channel: %.1fs / %.1fs",
                        Math.max(0d, snapshot.channelProgressSeconds()),
                        snapshot.requiredChannelSeconds());
            }
            return "Nodes: " + snapshot.activatedPoints() + " / " + snapshot.totalPoints();
        }

        @Nonnull
        private static TraceState traceState(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
            for (VampiricRitualPointState pointState : snapshot.pointStates()) {
                if (pointState.tracing()) {
                    return new TraceState(
                            "Tracing: " + pointState.symbolName()
                                    + " " + pointState.traceProgress()
                                    + " / " + pointState.totalTraceSteps(),
                            true);
                }
            }
            return new TraceState("", false);
        }

        @Nonnull
        private static String formatPhase(@Nonnull VampiricRitualRuntimePhase phase) {
            String name = phase.name().toLowerCase(Locale.ROOT);
            return Character.toUpperCase(name.charAt(0)) + name.substring(1);
        }

        @Nonnull
        private static String formatRounded(double value) {
            return Long.toString(Math.round(value));
        }
    }

    private record TraceState(@Nonnull String text, boolean visible) {
    }
}
