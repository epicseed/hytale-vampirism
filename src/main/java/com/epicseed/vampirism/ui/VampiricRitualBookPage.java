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
            cmd.set("#RequirementsText.Text", "");
            cmd.set("#BlockersText.Text", "");
            cmd.set("#ObjectivesText.Text", "");
            cmd.set("#RewardsText.Text", "");
            cmd.set("#AttuneBtnLabel.Text", "Attune Ritual");
            cmd.set("#AttuneBtn.Disabled", true);
            cmd.set("#DiagramAnchorCore.Background", "#6c4f3f");
            return;
        }

        renderTabsState(cmd);
        renderPoints(cmd);

        cmd.set("#BookTitle.Text", model.bookTitle());
        cmd.set("#RitualIcon.Background", model.iconPath());
        cmd.set("#DescriptionText.Text", model.descriptionText());
        cmd.set("#OverviewLine.Text", model.overviewLine());
        cmd.set("#RequirementsText.Text", model.requirementsText());
        cmd.set("#BlockersText.Text", model.blockingText());
        cmd.set("#ObjectivesText.Text", model.objectivesText());
        cmd.set("#RewardsText.Text", model.rewardsText());
        cmd.set("#AttuneBtnLabel.Text", model.attuneButtonText());
        cmd.set("#AttuneBtn.Disabled", false);
        cmd.set("#DiagramAnchorCore.Background", "#6c4f3f");
    }

    private void renderTabsState(@Nonnull UICommandBuilder cmd) {
        int rightCount = rightTabCount(model.rituals().size());
        for (int i = 0; i < model.rituals().size(); i++) {
            boolean right = i < rightCount;
            int indexOnSide = right ? i : i - rightCount;
            String selector = (right ? "#RightTabGutter" : "#LeftTabGutter") + "[" + indexOnSide + "]";
            VampiricRitualBookModel.RitualEntry entry = model.rituals().get(i);
            boolean selected = entry.ritualId().equals(model.selectedRitualId());
            cmd.set(selector + " #TabIcon.Background", entry.iconPath());
            cmd.set(selector + " #TabTitle.Text", entry.definition().displayName());
            cmd.set(selector + " #TabPlate.Background", tabPlateColor(selected));
            cmd.set(selector + " #TabTitle.Style.TextColor", selected ? "#f6e7c6" : "#d2b59a");
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
            cmd.setObject(selector + ".Anchor", anchor(point.left(), point.top(), 48, 48));
            cmd.set(selector + " #PointPlate.Background", point.fillColor());
            cmd.set(selector + " #PointGlyph.Background", point.symbolTexturePath());
            cmd.set(selector + " #PointIndex.Text", Integer.toString(point.index()));
            cmd.set(selector + " #PointIndex.Style.TextColor", point.textColor());
            cmd.set(selector + " #PointGlow.Visible", false);
            cmd.set(selector + " #PointDormantShade.Visible", false);
            cmd.set(selector + " #PointTooltip.TooltipText", point.symbolName());
        }
    }

    private void notifyAttuned(@Nonnull String displayName) {
        Message message = Message.join(
                Message.raw("Selected ").color("aqua"),
                Message.raw(displayName).color("white"),
                Message.raw(" for " + model.attunementScope() + ".").color("gray"));
        PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, message, NotificationStyle.Success, message);
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
    private static String tabPlateColor(boolean selected) {
        if (selected) {
            return "#6b4133";
        }
        return "#50372c";
    }
}
