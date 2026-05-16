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
    private static final String INLINE_MARKUP = """
            $C = "../../Common.ui";

            @BottomHudClearance = 302;

            $C.@Panel #NightHuntHudRoot {
              Anchor: (Left: 24, Bottom: @BottomHudClearance, Width: 388, Height: 112);
              Visible: false;
              HitTestVisible: false;
              Padding: (Full: 10);

              Group #CompactStrip {
                Anchor: (Left: 0, Right: 0, Top: 0, Height: 38);
                HitTestVisible: false;

                Group #PhaseChip {
                  Anchor: (Left: 0, Top: 3, Width: 112, Height: 24);
                  Background: #34161f(0.94);
                  HitTestVisible: false;

                  Label #PhaseValue {
                    Anchor: (Full: 0);
                    Text: "";
                    Style: (FontSize: 10, TextColor: #f6dce3, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center);
                    HitTestVisible: false;
                  }
                }

                Label #HuntName {
                  Anchor: (Left: 124, Top: 0, Right: 108, Height: 18);
                  Text: "";
                  Style: (FontName: "Secondary", FontSize: 15, TextColor: #f7d2d8, RenderBold: true);
                  HitTestVisible: false;
                }

                Label #ProgressValue {
                  Anchor: (Right: 0, Top: 0, Width: 88, Height: 18);
                  Text: "";
                  Style: (FontSize: 10, TextColor: #f7d2d8, RenderBold: true, HorizontalAlignment: End);
                  HitTestVisible: false;
                }

                Label #GuidanceValue {
                  Anchor: (Left: 124, Top: 20, Right: 0, Height: 16);
                  Text: "";
                  Style: (FontSize: 11, TextColor: $C.@ColorDefaultLabel, RenderBold: true);
                  HitTestVisible: false;
                }
              }

              $C.@ContentSeparator {
                Anchor: (Left: 0, Right: 0, Top: 46, Height: 1);
              }

              Label #ContextValue {
                Anchor: (Left: 0, Right: 0, Top: 56, Height: 18);
                Text: "";
                Style: (FontSize: 11, TextColor: #f0e2e6, RenderBold: true);
                HitTestVisible: false;
              }

              Label #TargetValue {
                Anchor: (Left: 0, Right: 0, Top: 80, Height: 18);
                Text: "";
                Style: (FontSize: 10, TextColor: #dcb6c0);
                HitTestVisible: false;
              }
            }
            """;

    private static final String ROOT = "#NightHuntHudRoot";
    private static final String PHASE_CHIP = ROOT + " #PhaseChip";
    private static final String TITLE = ROOT + " #HuntName";
    private static final String PHASE = ROOT + " #PhaseValue";
    private static final String GUIDANCE = ROOT + " #GuidanceValue";
    private static final String PROGRESS = ROOT + " #ProgressValue";
    private static final String CONTEXT = ROOT + " #ContextValue";
    private static final String TARGET = ROOT + " #TargetValue";

    private NightHuntHudPresentation.DisplayState state = NightHuntHudPresentation.DisplayState.hidden();

    public NightHuntStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    @Nonnull
    protected String documentPath() {
        return VampirismUiPaths.nightHuntHudLayout();
    }

    @Override
    @Nullable
    protected String inlineMarkup() {
        return INLINE_MARKUP;
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
