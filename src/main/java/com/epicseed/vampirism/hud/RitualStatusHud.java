package com.epicseed.vampirism.hud;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
    private static final String GUIDANCE = "#GuidanceValue";
    private static final String PROGRESS = "#ProgressValue";
    private static final String EXPANDED = "#ExpandedSection";
    private static final String CONTEXT = "#ContextValue";
    private static final String STABILITY = "#StabilityValue";
    private static final String CORRUPTION = "#CorruptionValue";

    @Nullable
    private VampiricRitualRuntimeSnapshot snapshot;
    private RitualHudDisplayMode displayMode = RitualHudDisplayMode.MINIMAL;
    private RitualHudPresentation.DisplayState state = RitualHudPresentation.DisplayState.hidden();

    public RitualStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder builder) {
        builder.append(VampirismUiPaths.ritualHudLayout());
        writeState(builder, state);
    }

    public void sync(@Nullable VampiricRitualRuntimeSnapshot snapshot) {
        sync(snapshot, displayMode);
    }

    public void sync(@Nullable VampiricRitualRuntimeSnapshot snapshot, @Nonnull RitualHudDisplayMode displayMode) {
        this.snapshot = snapshot;
        this.displayMode = Objects.requireNonNull(displayMode, "displayMode");
        RitualHudPresentation.DisplayState nextState = RitualHudPresentation.present(snapshot, displayMode);
        if (state.equals(nextState)) {
            return;
        }
        state = nextState;
        pushState();
    }

    public void syncDisplayMode(@Nonnull RitualHudDisplayMode displayMode) {
        sync(snapshot, displayMode);
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

    private static void writeState(@Nonnull UICommandBuilder builder,
                                   @Nonnull RitualHudPresentation.DisplayState state) {
        builder.set(ROOT + ".Visible", state.visible());
        builder.set(TITLE + ".Text", state.title());
        builder.set(PHASE + ".Text", state.phase());
        builder.set(GUIDANCE + ".Text", state.guidance());
        builder.set(PROGRESS + ".Text", state.progress());
        builder.set(EXPANDED + ".Visible", state.expandedVisible());
        builder.set(CONTEXT + ".Text", state.context());
        builder.set(STABILITY + ".Text", state.stability());
        builder.set(CORRUPTION + ".Text", state.corruption());
    }
}
