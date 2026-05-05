package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.ritual.VampiricRitualTemplateRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualEditorPage extends InteractiveCustomUIPage<RitualEditorEventData> {

    private static final double NUDGE = 0.02d;

    private final VampiricRitualEditorModel model;

    public VampiricRitualEditorPage(@Nonnull PlayerRef playerRef,
                                    @Nonnull VampiricRitualTemplateRegistry templateRegistry) {
        super(playerRef, CustomPageLifetime.CanDismiss, RitualEditorEventData.CODEC);
        this.model = VampiricRitualEditorModel.fromRegistry(templateRegistry);
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(VampirismUiPaths.ritualEditorLayout());
        bind(events);
        render(cmd);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull RitualEditorEventData data) {
        if (data.action == null) {
            sendUpdate(new UICommandBuilder());
            return;
        }

        if ("close".equals(data.action)) {
            Player player = store.getComponent(ref, Player.getComponentType());
            if (player != null) {
                player.getPageManager().setPage(ref, store, Page.None);
            }
            sendUpdate();
            return;
        }

        switch (data.action) {
            case "template_prev" -> model.cycleTemplate(-1);
            case "template_next" -> model.cycleTemplate(1);
            case "point_prev" -> model.cyclePoint(-1);
            case "point_next" -> model.cyclePoint(1);
            case "symbol_prev" -> model.cycleSymbol(-1);
            case "symbol_next" -> model.cycleSymbol(1);
            case "step_prev" -> model.cycleStep(-1);
            case "step_next" -> model.cycleStep(1);
            case "step_add" -> model.addStepAfterSelection();
            case "step_remove" -> model.removeSelectedStep();
            case "reset_template" -> model.resetSelectedTemplate();
            case "point_x_dec" -> model.nudgePoint('x', -NUDGE);
            case "point_x_inc" -> model.nudgePoint('x', NUDGE);
            case "point_y_dec" -> model.nudgePoint('y', -NUDGE);
            case "point_y_inc" -> model.nudgePoint('y', NUDGE);
            case "point_z_dec" -> model.nudgePoint('z', -NUDGE);
            case "point_z_inc" -> model.nudgePoint('z', NUDGE);
            case "step_x_dec" -> model.nudgeStep('x', -NUDGE);
            case "step_x_inc" -> model.nudgeStep('x', NUDGE);
            case "step_y_dec" -> model.nudgeStep('y', -NUDGE);
            case "step_y_inc" -> model.nudgeStep('y', NUDGE);
            case "step_z_dec" -> model.nudgeStep('z', -NUDGE);
            case "step_z_inc" -> model.nudgeStep('z', NUDGE);
            case "export_point" -> model.exportSelectedPoint();
            case "export_template" -> model.exportSelectedTemplate();
            default -> {
            }
        }

        UICommandBuilder cmd = new UICommandBuilder();
        render(cmd);
        sendUpdate(cmd);
    }

    private void bind(@Nonnull UIEventBuilder events) {
        bind(events, "#CloseBtn", "close");
        bind(events, "#TemplatePrevBtn", "template_prev");
        bind(events, "#TemplateNextBtn", "template_next");
        bind(events, "#PointPrevBtn", "point_prev");
        bind(events, "#PointNextBtn", "point_next");
        bind(events, "#SymbolPrevBtn", "symbol_prev");
        bind(events, "#SymbolNextBtn", "symbol_next");
        bind(events, "#StepPrevBtn", "step_prev");
        bind(events, "#StepNextBtn", "step_next");
        bind(events, "#AddStepBtn", "step_add");
        bind(events, "#RemoveStepBtn", "step_remove");
        bind(events, "#ResetTemplateBtn", "reset_template");
        bind(events, "#ExportPointBtn", "export_point");
        bind(events, "#ExportTemplateBtn", "export_template");
        bind(events, "#PointXDecBtn", "point_x_dec");
        bind(events, "#PointXIncBtn", "point_x_inc");
        bind(events, "#PointYDecBtn", "point_y_dec");
        bind(events, "#PointYIncBtn", "point_y_inc");
        bind(events, "#PointZDecBtn", "point_z_dec");
        bind(events, "#PointZIncBtn", "point_z_inc");
        bind(events, "#StepXDecBtn", "step_x_dec");
        bind(events, "#StepXIncBtn", "step_x_inc");
        bind(events, "#StepYDecBtn", "step_y_dec");
        bind(events, "#StepYIncBtn", "step_y_inc");
        bind(events, "#StepZDecBtn", "step_z_dec");
        bind(events, "#StepZIncBtn", "step_z_inc");
    }

    private void bind(@Nonnull UIEventBuilder events,
                      @Nonnull String selector,
                      @Nonnull String action) {
        events.addEventBinding(
                CustomUIEventBindingType.Activating,
                selector,
                new EventData().append("Action", action),
                false);
    }

    private void render(@Nonnull UICommandBuilder cmd) {
        cmd.set("#TemplateValue.Text", model.selectedTemplateName());
        cmd.set("#TemplateSummary.Text", model.templateSummary());
        cmd.set("#PointsValue.Text", model.pointsOverview());
        cmd.set("#PointValue.Text", model.selectedPointName());
        cmd.set("#PointSummary.Text", model.pointSummary());
        cmd.set("#SymbolValue.Text", model.selectedSymbolName());
        cmd.set("#GlyphPreview.Background", model.symbolTexturePath());
        cmd.set("#StepValue.Text", model.selectedStepName());
        cmd.set("#StepSummary.Text", model.stepSummary());
        cmd.set("#StepListValue.Text", model.stepsPreview());
        cmd.set("#ExportTitle.Text", model.exportTitle());
        cmd.set("#ExportText.Text", model.exportText());
    }
}
