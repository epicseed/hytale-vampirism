package com.epicseed.vampirism.hud;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.hud.ComposableCustomHud;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.ui.VampirismUiPaths;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

public final class RitualStatusHud extends ComposableCustomHud {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String ROOT = "#RitualHudRoot";
    private static final String TITLE = ROOT + " #RitualName";
    private static final String PHASE = ROOT + " #PhaseValue";
    private static final String GUIDANCE = ROOT + " #GuidanceValue";
    private static final String PROGRESS = ROOT + " #ProgressValue";
    private static final String SEPARATOR = ROOT + " #DetailSeparator";
    private static final String DETAIL_SECTION = ROOT + " #DetailSection";
    private static final int ROOT_LEFT = 24;
    private static final int ROOT_BOTTOM = 180;
    private static final int ROOT_WIDTH = 360;
    private static final int ROOT_MINIMAL_HEIGHT = 58;
    private static final int ROOT_DETAIL_OVERHEAD = 78;
    private static final int DETAIL_TOP = 52;
    private static final int DETAIL_ROW_GAP = 4;
    private static final int MAX_CHECKLIST_ROWS = 12;
    private static final int CHECKLIST_PROGRESS_WIDTH = 64;

    @Nullable
    private VampiricRitualRuntimeSnapshot snapshot;
    private RitualHudDisplayMode displayMode = RitualHudDisplayMode.MINIMAL;
    private RitualHudPresentation.DisplayState state = RitualHudPresentation.DisplayState.hidden();

    public RitualStatusHud(@Nonnull PlayerRef playerRef, @Nonnull String hudKey) {
        super(playerRef, hudKey);
    }

    @Override
    @Nonnull
    protected String documentPath() {
        return VampirismUiPaths.ritualHudLayout();
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
        builder.set(PROGRESS + ".Style.TextColor", state.progressColor());
        builder.setObject(ROOT + ".Anchor", rootAnchor(rootHeight(state)));
        builder.set(SEPARATOR + ".Visible", state.expandedVisible());
        builder.set(DETAIL_SECTION + ".Visible", state.expandedVisible());
        builder.setObject(DETAIL_SECTION + ".Anchor", detailSectionAnchor(detailHeight(state)));
        writeChecklist(builder, state);
    }

    private static void writeChecklist(@Nonnull UICommandBuilder builder,
                                       @Nonnull RitualHudPresentation.DisplayState state) {
        int top = 0;
        int visibleCount = Math.min(MAX_CHECKLIST_ROWS, state.checklistRows().size());
        for (int i = 0; i < MAX_CHECKLIST_ROWS; i++) {
            String selector = ROOT + " #HudChecklistRow" + i;
            if (i >= visibleCount) {
                builder.set(selector + ".Visible", false);
                continue;
            }
            RitualHudPresentation.ChecklistRow row = visibleRow(state, i);
            int rowHeight = Math.max(20, row.height());
            builder.set(selector + ".Visible", state.expandedVisible());
            builder.setObject(selector + ".Anchor", rowAnchor(top, rowHeight));
            builder.setObject(selector + " #HudChecklistIcon.Anchor", anchor(0, 0, 28, rowHeight));
            builder.set(selector + " #HudChecklistIcon.Background", row.color());
            builder.set(selector + " #HudChecklistMark.Text", row.mark());
            builder.set(selector + " #HudChecklistTitle.Text", row.title());
            builder.set(selector + " #HudChecklistDetail.Text", row.detail());
            builder.set(selector + " #HudChecklistProgressTrack.Visible", row.hasProgress());
            if (row.hasProgress()) {
                int width = Math.max(2, Math.min(CHECKLIST_PROGRESS_WIDTH,
                        (int) Math.round(CHECKLIST_PROGRESS_WIDTH * (row.progressCurrent() / row.progressTarget()))));
                builder.setObject(selector + " #HudChecklistProgressTrack.Anchor", progressTrackAnchor(rowHeight));
                builder.set(selector + " #HudChecklistProgressFill.Background", row.color());
                builder.setObject(selector + " #HudChecklistProgressFill.Anchor", anchor(0, 0, width, 5));
            }
            top += rowHeight + DETAIL_ROW_GAP;
        }
    }

    @Nonnull
    private static RitualHudPresentation.ChecklistRow visibleRow(@Nonnull RitualHudPresentation.DisplayState state,
                                                                 int index) {
        if (index == MAX_CHECKLIST_ROWS - 1 && state.checklistRows().size() > MAX_CHECKLIST_ROWS) {
            return new RitualHudPresentation.ChecklistRow(
                    "+" + (state.checklistRows().size() - index),
                    "More checks",
                    "Additional ritual requirements",
                    "#5b4638",
                    0,
                    0,
                    28);
        }
        return state.checklistRows().get(index);
    }

    private static int rootHeight(@Nonnull RitualHudPresentation.DisplayState state) {
        if (!state.expandedVisible()) {
            return ROOT_MINIMAL_HEIGHT;
        }
        return ROOT_DETAIL_OVERHEAD + detailHeight(state);
    }

    private static int detailHeight(@Nonnull RitualHudPresentation.DisplayState state) {
        if (!state.expandedVisible() || state.checklistRows().isEmpty()) {
            return 0;
        }
        int visibleCount = Math.min(MAX_CHECKLIST_ROWS, state.checklistRows().size());
        int height = 0;
        for (int i = 0; i < visibleCount; i++) {
            height += Math.max(20, visibleRow(state, i).height());
            if (i < visibleCount - 1) {
                height += DETAIL_ROW_GAP;
            }
        }
        return height;
    }

    @Nonnull
    private static Anchor rootAnchor(int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(ROOT_LEFT));
        anchor.setBottom(Value.of(ROOT_BOTTOM));
        anchor.setWidth(Value.of(ROOT_WIDTH));
        anchor.setHeight(Value.of(height));
        return anchor;
    }

    @Nonnull
    private static Anchor detailSectionAnchor(int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(0));
        anchor.setRight(Value.of(0));
        anchor.setTop(Value.of(DETAIL_TOP));
        anchor.setHeight(Value.of(height));
        return anchor;
    }

    @Nonnull
    private static Anchor rowAnchor(int top, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(0));
        anchor.setRight(Value.of(0));
        anchor.setTop(Value.of(top));
        anchor.setHeight(Value.of(height));
        return anchor;
    }

    @Nonnull
    private static Anchor progressTrackAnchor(int rowHeight) {
        Anchor anchor = new Anchor();
        anchor.setRight(Value.of(8));
        anchor.setTop(Value.of(Math.max(7, (rowHeight - 5) / 2)));
        anchor.setWidth(Value.of(CHECKLIST_PROGRESS_WIDTH));
        anchor.setHeight(Value.of(5));
        return anchor;
    }

    @Nonnull
    private static Anchor anchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left));
        anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        return anchor;
    }
}
