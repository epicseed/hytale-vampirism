package com.epicseed.vampirism.ui;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplate;
import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplateRegistry;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualSelectionService;
import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualTargeting.TargetedBlock;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualBookPage extends InteractiveCustomUIPage<RitualBookEventData> {
    private static final int TAB_WIDTH = 104;
    private static final int TAB_HEIGHT = 30;
    private static final int TAB_GAP = 8;
    private static final int TAB_GUTTER_HEIGHT = 552;
    private static final int TAB_MAX_PER_SIDE = (TAB_GUTTER_HEIGHT + TAB_GAP) / (TAB_HEIGHT + TAB_GAP);
    private static final int PREVIEW_POINT_SIZE = 64;
    private static final int MAX_CHECKLIST_ROWS = 8;
    private static final int MAX_REWARD_CHIPS = 3;
    private static final int CHECKLIST_PROGRESS_WIDTH = 90;
    private static final String PREVIEW_BASE_TEXTURE = "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Base_Book.png";
    private static final String PREVIEW_NODE_TEXTURE = "Vampirism/Assets/Rituals/Vampirism_RitualGlyph_Node_Inactive.png";

    private final VampiricRitualSelectionService selectionService;
    private final TargetedBlock anchor;
    private final VampiricRitualBookModel model;
    private final int pointSlotCount;
    @Nullable
    private final String activeRitualId;
    private String attunedRitualId;

    public VampiricRitualBookPage(@Nonnull PlayerRef playerRef,
                                  @Nonnull VampiricRitualService ritualService,
                                  @Nonnull VampiricRitualRuntimeService runtimeService,
                                  @Nonnull VampiricRitualTemplateRegistry templateRegistry,
                                  @Nonnull VampiricRitualSelectionService selectionService,
                                  @Nonnull TargetedBlock anchor,
                                  @Nonnull VampiricRitualContext ritualContext,
                                  @Nullable String activeRitualId) {
        super(playerRef, CustomPageLifetime.CanDismiss, RitualBookEventData.CODEC);
        this.selectionService = Objects.requireNonNull(selectionService, "selectionService");
        this.anchor = Objects.requireNonNull(anchor, "anchor");

        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> resolved = runtimeService.listRitualsForAnchor(anchor.blockId());
        Map<String, VampiricRitualDefinition> definitions = new LinkedHashMap<>();
        Map<String, VampiricRitualTemplate> templates = new LinkedHashMap<>();
        Map<String, VampiricRitualEvaluation> evaluations = new LinkedHashMap<>();
        for (VampiricRitualRuntimeService.ResolvedAnchorRitual ritual : resolved) {
            ritualService.registry().definition(ritual.ritualId()).ifPresent(definition -> definitions.put(ritual.ritualId(), definition));
            templateRegistry.template(ritual.ritualId()).ifPresent(template -> templates.put(ritual.ritualId(), template));
            if (definitions.containsKey(ritual.ritualId())) {
                evaluations.put(ritual.ritualId(), ritualService.evaluate(playerRef.getUuid(), ritual.ritualId(), ritualContext));
            }
        }
        this.attunedRitualId = selectionService.selectedRitual(playerRef.getUuid(), anchor.blockId()).orElse(null);
        this.activeRitualId = activeRitualId;
        String initiallySelectedRitualId = activeRitualId != null ? activeRitualId : attunedRitualId;
        this.model = VampiricRitualBookModel.create(anchor.blockId(), resolved, definitions, templates, evaluations, initiallySelectedRitualId);
        this.pointSlotCount = Math.max(1, model.rituals().stream()
                .mapToInt(entry -> entry.template().points().size())
                .max()
                .orElse(1));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(VampirismUiPaths.ritualBookLayout());
        appendTabs(cmd, events);
        appendPointSlots(cmd);
        bindStatic(events);
        render(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull RitualBookEventData data) {
        if (data.action == null) {
            sendUpdate();
            return;
        }

        Player player = store.getComponent(ref, Player.getComponentType());

        switch (data.action) {
            case "close" -> {
                if (player != null) {
                    player.getPageManager().setPage(ref, store, Page.None);
                }
                sendUpdate();
                return;
            }
            case "selectRitual" -> model.selectRitual(data.ritualId);
            case "attune" -> {
                String ritualId = model.selectedRitualId();
                if (ritualId != null) {
                    selectionService.select(playerRef.getUuid(), anchor.blockId(), ritualId);
                    attunedRitualId = ritualId;
                    notifyAttuned(model.bookTitle());
                }
            }
            default -> {
            }
        }

        UICommandBuilder cmd = new UICommandBuilder();
        render(cmd);
        sendUpdate(cmd);
    }

    private void bindStatic(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn",
                new EventData().append("Action", "close"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#AttuneBtn",
                new EventData().append("Action", "attune"), false);
    }

    private void appendTabs(@Nonnull UICommandBuilder cmd,
                            @Nonnull UIEventBuilder events) {
        int total = model.rituals().size();
        int rightCount = rightTabCount(total);
        int leftCount = Math.max(0, total - rightCount);
        for (int i = 0; i < model.rituals().size(); i++) {
            boolean right = i < rightCount;
            int indexOnSide = right ? i : i - rightCount;
            String target = right ? "#RightTabGutter" : "#LeftTabGutter";
            String selector = target + "[" + indexOnSide + "]";
            cmd.append(target, "Vampirism/Screens/RitualBookTab.ui");
            cmd.setObject(selector + ".Anchor", tabAnchor(right, indexOnSide));
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector + " #TabButton",
                    new EventData().append("Action", "selectRitual").append("RitualId", model.rituals().get(i).ritualId()),
                    false);
        }
        if (leftCount == 0) {
            cmd.set("#LeftTabGutter.Visible", false);
        }
        if (rightCount == 0) {
            cmd.set("#RightTabGutter.Visible", false);
        }
    }

    private void appendPointSlots(@Nonnull UICommandBuilder cmd) {
        for (int i = 0; i < pointSlotCount; i++) {
            cmd.append("#DiagramCanvas", "Vampirism/Screens/RitualBookPoint.ui");
        }
    }

    private void render(@Nonnull UICommandBuilder cmd) {
        if (model.empty()) {
            cmd.set("#BookTitle.Text", "No ritual answers this anchor");
            cmd.set("#RitualIcon.Background", VampirismUiPaths.theme().wipIcon());
            cmd.set("#DescriptionText.Text", "This anchor has no loaded rituals.");
            cmd.set("#OverviewLine.Text", "");
            clearChecklist(cmd);
            clearRewardChips(cmd);
            cmd.set("#AttuneBtnLabel.Text", "Attuned");
            cmd.set("#AttuneBtn.Disabled", true);
            cmd.set("#DiagramBaseGlyph.Background", PREVIEW_BASE_TEXTURE);
            return;
        }

        renderTabsState(cmd);
        renderPoints(cmd);

        cmd.set("#BookTitle.Text", model.bookTitle());
        cmd.set("#RitualIcon.Background", model.iconPath());
        cmd.set("#DescriptionText.Text", model.descriptionText());
        cmd.set("#OverviewLine.Text", model.overviewLine());
        renderChecklist(cmd);
        renderRewardChips(cmd);
        cmd.set("#AttuneBtnLabel.Text", attuneButtonText());
        cmd.set("#AttuneBtn.Disabled", selectedRitualAlreadyAttuned());
        cmd.set("#DiagramBaseGlyph.Background", PREVIEW_BASE_TEXTURE);
    }

    private void renderTabsState(@Nonnull UICommandBuilder cmd) {
        int rightCount = rightTabCount(model.rituals().size());
        for (int i = 0; i < model.rituals().size(); i++) {
            boolean right = i < rightCount;
            int indexOnSide = right ? i : i - rightCount;
            String selector = (right ? "#RightTabGutter" : "#LeftTabGutter") + "[" + indexOnSide + "]";
            VampiricRitualBookModel.RitualEntry entry = model.rituals().get(i);
            boolean selected = entry.ritualId().equals(model.selectedRitualId());
            boolean completed = entry.completed();
            String tabColor = tabPlateColor(selected, completed);
            cmd.set(selector + " #TabIcon.Background", entry.iconPath());
            cmd.set(selector + " #TabTitle.Text", entry.definition().displayName());
            cmd.set(selector + " #TabPlate.Background", tabColor);
            cmd.set(selector + " #TabInset.Background", tabColor);
            cmd.set(selector + " #TabSelectionAccent.Visible", selected);
            cmd.set(selector + " #TabCompletionAccent.Visible", completed);
            cmd.set(selector + " #TabTitle.Style.TextColor", tabTitleColor(selected, completed));
            cmd.set(selector + " #TabButton.TooltipText",
                    entry.definition().displayName()
                            + "\n\n"
                            + entry.definition().description());
        }
    }

    private void renderPoints(@Nonnull UICommandBuilder cmd) {
        List<VampiricRitualBookModel.PointView> points = model.pointViews();
        for (int i = 0; i < pointSlotCount; i++) {
            String selector = "#DiagramCanvas[" + i + "]";
            if (i >= points.size()) {
                cmd.set(selector + ".Visible", false);
                continue;
            }
            VampiricRitualBookModel.PointView point = points.get(i);
            cmd.set(selector + ".Visible", true);
            cmd.setObject(selector + ".Anchor", anchor(point.left(), point.top(), PREVIEW_POINT_SIZE, PREVIEW_POINT_SIZE));
            cmd.set(selector + " #PointPlate.Background", PREVIEW_NODE_TEXTURE);
            cmd.set(selector + " #PointGlyph.Background", point.symbolTexturePath());
            cmd.set(selector + " #PointIndex.Text", Integer.toString(point.index()));
            cmd.set(selector + " #PointIndex.Style.TextColor", point.textColor());
            cmd.set(selector + " #PointGlow.Visible", false);
            cmd.set(selector + " #PointDormantShade.Visible", false);
            cmd.set(selector + " #PointTooltip.TooltipText", point.symbolName());
        }
    }

    private void renderChecklist(@Nonnull UICommandBuilder cmd) {
        List<VampiricRitualBookModel.ChecklistItem> items = model.checklistItems();
        for (int i = 0; i < MAX_CHECKLIST_ROWS; i++) {
            String selector = "#ChecklistRow" + i;
            if (i >= items.size()) {
                cmd.set(selector + ".Visible", false);
                continue;
            }
            VampiricRitualBookModel.ChecklistItem item = items.get(i);
            if (i == MAX_CHECKLIST_ROWS - 1 && items.size() > MAX_CHECKLIST_ROWS) {
                item = new VampiricRitualBookModel.ChecklistItem(
                        "+" + (items.size() - i),
                        "More checks",
                        "Additional conditions are hidden.",
                        "#5b4638",
                        0,
                        0);
            }
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #ChecklistIcon" + i + ".Background", item.color());
            cmd.set(selector + " #ChecklistMark" + i + ".Text", item.mark());
            cmd.set(selector + " #ChecklistTitle" + i + ".Text", item.title());
            cmd.set(selector + " #ChecklistDetail" + i + ".Text", item.detail());
            cmd.set(selector + " #ChecklistProgressTrack" + i + ".Visible", item.hasProgress());
            if (item.hasProgress()) {
                int width = Math.max(2, Math.min(CHECKLIST_PROGRESS_WIDTH,
                        (int) Math.round(CHECKLIST_PROGRESS_WIDTH * (item.progressCurrent() / (double) item.progressTarget()))));
                cmd.set(selector + " #ChecklistProgressFill" + i + ".Background", item.color());
                cmd.setObject(selector + " #ChecklistProgressFill" + i + ".Anchor", anchor(0, 0, width, 5));
            }
        }
    }

    private void clearChecklist(@Nonnull UICommandBuilder cmd) {
        for (int i = 0; i < MAX_CHECKLIST_ROWS; i++) {
            cmd.set("#ChecklistRow" + i + ".Visible", false);
        }
    }

    private void renderRewardChips(@Nonnull UICommandBuilder cmd) {
        List<VampiricRitualBookModel.RewardView> rewards = model.rewardViews();
        for (int i = 0; i < MAX_REWARD_CHIPS; i++) {
            String selector = "#RewardChip" + i;
            if (i >= rewards.size()) {
                cmd.set(selector + ".Visible", false);
                continue;
            }
            VampiricRitualBookModel.RewardView reward = rewards.get(i);
            if (i == MAX_REWARD_CHIPS - 1 && rewards.size() > MAX_REWARD_CHIPS) {
                reward = new VampiricRitualBookModel.RewardView("+" + (rewards.size() - i), "more rewards");
            }
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #RewardMark" + i + ".Text", reward.mark());
            cmd.set(selector + " #RewardText" + i + ".Text", reward.text());
        }
    }

    private void clearRewardChips(@Nonnull UICommandBuilder cmd) {
        for (int i = 0; i < MAX_REWARD_CHIPS; i++) {
            cmd.set("#RewardChip" + i + ".Visible", false);
        }
    }

    private void notifyAttuned(@Nonnull String displayName) {
        Message message = Message.join(
                Message.raw("Selected ").color("aqua"),
                Message.raw(displayName).color("white"),
                Message.raw(" for " + model.attunementScope() + ".").color("gray"));
        PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, message, NotificationStyle.Success, message);
    }

    @Nonnull
    private String attuneButtonText() {
        return selectedRitualAlreadyAttuned() ? "Attuned" : model.attuneButtonText();
    }

    private boolean selectedRitualAlreadyAttuned() {
        return Objects.equals(model.selectedRitualId(), attunedRitualId);
    }

    private static int rightTabCount(int total) {
        return Math.min(total, TAB_MAX_PER_SIDE);
    }

    @Nonnull
    private static Anchor tabAnchor(boolean right, int index) {
        int top = index * (TAB_HEIGHT + TAB_GAP);
        Anchor anchor = new Anchor();
        anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(TAB_WIDTH));
        anchor.setHeight(Value.of(TAB_HEIGHT));
        if (right) {
            anchor.setRight(Value.of(0));
        } else {
            anchor.setLeft(Value.of(0));
        }
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

    @Nonnull
    private static String humanizeAnchor(@Nonnull String anchorBlockId) {
        return "Furniture_Ancient_Coffin".equals(anchorBlockId) ? "Ancient Coffin" : anchorBlockId;
    }

    @Nonnull
    private static String tabPlateColor(boolean selected, boolean completed) {
        if (selected) {
            return "#7a5140";
        }
        if (completed) {
            return "#6a5334";
        }
        return "#50372c";
    }

    @Nonnull
    private static String tabTitleColor(boolean selected, boolean completed) {
        if (selected) {
            return "#f6e7c6";
        }
        if (completed) {
            return "#e4cb8f";
        }
        return "#d2b59a";
    }
}
