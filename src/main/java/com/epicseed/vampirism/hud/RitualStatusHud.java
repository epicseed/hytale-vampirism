package com.epicseed.vampirism.hud;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.hud.ComposableCustomHud;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class RitualStatusHud extends ComposableCustomHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String INLINE_MARKUP = """
            $C = "../../Common.ui";

            @BottomHudClearance = 180;

            $C.@Panel #RitualHudRoot {
              Anchor: (Left: 24, Bottom: @BottomHudClearance, Width: 360, Height: 112);
              Visible: false;
              HitTestVisible: false;
              Padding: (Full: 10);

              Group #CompactStrip {
                Anchor: (Left: 0, Right: 0, Top: 0, Height: 38);
                HitTestVisible: false;

                Group #PhaseChip {
                  Anchor: (Left: 0, Top: 4, Width: 84, Height: 22);
                  Background: #34161f(0.94);
                  HitTestVisible: false;

                  Label #PhaseValue {
                    Anchor: (Full: 0);
                    Text: "";
                    Style: (FontSize: 10, TextColor: #f6dce3, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center);
                    HitTestVisible: false;
                  }
                }

                Label #RitualName {
                  Anchor: (Left: 96, Top: 0, Right: 88, Height: 18);
                  Text: "";
                  Style: (FontName: "Secondary", FontSize: 15, TextColor: #f7d2d8, RenderBold: true);
                  HitTestVisible: false;
                }

                Label #ProgressValue {
                  Anchor: (Right: 0, Top: 0, Width: 82, Height: 18);
                  Text: "";
                  Style: (FontSize: 10, TextColor: $C.@ColorGrayCaption, RenderBold: true, HorizontalAlignment: End);
                  HitTestVisible: false;
                }

                Label #GuidanceValue {
                  Anchor: (Left: 96, Top: 20, Right: 0, Height: 16);
                  Text: "";
                  Style: (FontSize: 11, TextColor: $C.@ColorDefaultLabel, RenderBold: true);
                  HitTestVisible: false;
                }
              }

              $C.@ContentSeparator {
                Anchor: (Left: 0, Right: 0, Top: 46, Height: 1);
              }

              Group #ExpandedSection {
                Anchor: (Left: 0, Right: 0, Bottom: 0, Height: 48);
                Visible: false;
                HitTestVisible: false;

                Label #ContextValue {
                  Anchor: (Left: 0, Right: 0, Top: 0, Height: 18);
                  Text: "";
                  Style: (FontSize: 11, TextColor: #f0e2e6, Wrap: true);
                  HitTestVisible: false;
                }

                Group #StabilityPill {
                  Anchor: (Left: 0, Top: 26, Width: 168, Height: 20);
                  Background: #14241c(0.9);
                  HitTestVisible: false;

                  Label #StabilityValue {
                    Anchor: (Left: 8, Right: 8, Top: 0, Height: 20);
                    Text: "";
                    Style: (FontSize: 10, TextColor: #d7e4dd, RenderBold: true, VerticalAlignment: Center);
                    HitTestVisible: false;
                  }
                }

                Group #CorruptionPill {
                  Anchor: (Right: 0, Top: 26, Width: 168, Height: 20);
                  Background: #31111b(0.92);
                  HitTestVisible: false;

                  Label #CorruptionValue {
                    Anchor: (Left: 8, Right: 8, Top: 0, Height: 20);
                    Text: "";
                    Style: (FontSize: 10, TextColor: #f3c3cf, RenderBold: true, VerticalAlignment: Center);
                    HitTestVisible: false;
                  }
                }
              }
            }
            """;

    private static final String ROOT = "#RitualHudRoot";
    private static final String TITLE = ROOT + " #RitualName";
    private static final String PHASE = ROOT + " #PhaseValue";
    private static final String GUIDANCE = ROOT + " #GuidanceValue";
    private static final String PROGRESS = ROOT + " #ProgressValue";
    private static final String EXPANDED = ROOT + " #ExpandedSection";
    private static final String CONTEXT = ROOT + " #ContextValue";
    private static final String STABILITY = ROOT + " #StabilityValue";
    private static final String CORRUPTION = ROOT + " #CorruptionValue";

    @Nullable
    private VampiricRitualRuntimeSnapshot snapshot;
    private RitualHudDisplayMode displayMode = RitualHudDisplayMode.MINIMAL;
    private RitualHudPresentation.DisplayState state = RitualHudPresentation.DisplayState.hidden();

    public RitualStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef);
    }

    @Override
    @Nonnull
    protected String documentPath() {
        return VampirismUiPaths.ritualHudLayout();
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
            dispatchUpdate(builder);
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
