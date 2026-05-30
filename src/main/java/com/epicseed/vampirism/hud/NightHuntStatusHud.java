package com.epicseed.vampirism.hud;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.epicseed.epiccore.hytale.hud.ComposableCustomHud;
import com.epicseed.epiccore.vampirism.domain.hunt.NightHuntStatusSnapshot;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class NightHuntStatusHud extends ComposableCustomHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ROOT = "#NightHuntHudRoot";
    private static final String PHASE_CHIP = ROOT + " #PhaseChip";
    private static final String TITLE = ROOT + " #HuntName";
    private static final String PHASE = ROOT + " #PhaseValue";
    private static final String GUIDANCE = ROOT + " #GuidanceValue";
    private static final String PROGRESS = ROOT + " #ProgressValue";
    private static final String CONTEXT = ROOT + " #ContextValue";
    private static final String TARGET = ROOT + " #TargetValue";

    private NightHuntHudPresentation.DisplayState state = NightHuntHudPresentation.DisplayState.hidden();

    public NightHuntStatusHud(@Nonnull PlayerRef playerRef, @Nonnull String hudKey) {
        super(playerRef, hudKey);
    }

    @Override
    @Nonnull
    protected String documentPath() {
        return VampirismUiPaths.nightHuntHudLayout();
    }

    @Override
    protected void writeCurrentState(@Nonnull UICommandBuilder builder) {
        writeState(builder, state);
    }

    @Override
    @Nonnull
    public String rootSelector() {
        return ROOT;
    }

    public void sync(@Nullable NightHuntStatusSnapshot snapshot) {
        NightHuntHudPresentation.DisplayState nextState = NightHuntHudPresentation.present(snapshot);
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
            dispatchUpdate(builder);
        } catch (Exception e) {
            LOGGER.atSevere().log("[NightHuntStatusHud] Error updating HUD: " + e.getMessage());
        }
    }

    private static void writeState(@Nonnull UICommandBuilder builder,
                                   @Nonnull NightHuntHudPresentation.DisplayState state) {
        builder.set(ROOT + ".Visible", state.visible());
        builder.set(PHASE_CHIP + ".Background", state.palette().chipBackground());
        builder.set(TITLE + ".Text", state.header());
        builder.set(TITLE + ".Style.TextColor", state.palette().headerText());
        builder.set(PHASE + ".Text", state.phase());
        builder.set(PHASE + ".Style.TextColor", state.palette().phaseText());
        builder.set(GUIDANCE + ".Text", state.guidance());
        builder.set(GUIDANCE + ".Style.TextColor", state.palette().guidanceText());
        builder.set(PROGRESS + ".Text", state.progress());
        builder.set(PROGRESS + ".Style.TextColor", state.palette().progressText());
        builder.set(CONTEXT + ".Text", state.context());
        builder.set(CONTEXT + ".Style.TextColor", state.palette().contextText());
        builder.set(TARGET + ".Text", state.target());
    }
}
