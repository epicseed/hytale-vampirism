package com.epicseed.vampirism.ui;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionService;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContextResolver;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
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

public final class HuntCompendiumPage extends InteractiveCustomUIPage<HuntCompendiumEventData> {

    private final ProgressionPageFactory pageFactory;
    private final HuntCompendiumNextRiteResolver nextRiteResolver;
    private final VampiricRitualContextResolver ritualContextResolver;
    private final VampiricLineageService lineageService;
    private final MasqueradeHeatService masqueradeHeatService;
    private HuntCompendiumModel.Tab selectedTab = HuntCompendiumModel.Tab.OVERVIEW;
    private String previewPreparationId;

    public HuntCompendiumPage(@Nonnull PlayerRef playerRef) {
        this(playerRef, null, null, null, null, null);
    }

    public HuntCompendiumPage(@Nonnull PlayerRef playerRef,
                              VampiricRitualService ritualService,
                              VampiricRitualContextResolver ritualContextResolver) {
        this(playerRef, null, ritualService, ritualContextResolver, null, null);
    }

    public HuntCompendiumPage(@Nonnull PlayerRef playerRef,
                              VampiricRitualService ritualService,
                              VampiricRitualContextResolver ritualContextResolver,
                              VampiricLineageService lineageService,
                              MasqueradeHeatService masqueradeHeatService) {
        this(playerRef, null, ritualService, ritualContextResolver, lineageService, masqueradeHeatService);
    }

    public HuntCompendiumPage(@Nonnull PlayerRef playerRef,
                              @Nullable ProgressionPageFactory pageFactory,
                              VampiricRitualService ritualService,
                              VampiricRitualContextResolver ritualContextResolver,
                              VampiricLineageService lineageService,
                              MasqueradeHeatService masqueradeHeatService) {
        super(playerRef, CustomPageLifetime.CanDismiss, HuntCompendiumEventData.CODEC);
        this.pageFactory = pageFactory;
        this.nextRiteResolver = ritualService != null ? new HuntCompendiumNextRiteResolver(ritualService) : null;
        this.ritualContextResolver = ritualContextResolver;
        this.lineageService = lineageService;
        this.masqueradeHeatService = masqueradeHeatService;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref,
                      @Nonnull UICommandBuilder cmd,
                      @Nonnull UIEventBuilder events,
                      @Nonnull Store<EntityStore> store) {
        cmd.append(VampirismUiPaths.huntCompendiumLayout());
        HuntCompendiumModel model = model(store);
        bindEvents(events);
        appendPreparationCards(cmd, model);
        bindPreparationEvents(events, model);
        render(cmd, model);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull HuntCompendiumEventData data) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (data.action == null) {
            sendUpdate();
            return;
        }

        switch (data.action) {
            case "close" -> {
                if (player != null) {
                    player.getPageManager().setPage(ref, store, Page.None);
                }
                sendUpdate();
                return;
            }
            case "openSkillTree" -> {
                openPage(player, ref, store, pageFactory != null ? pageFactory.createSkillTreePage(playerRef) : null);
                sendUpdate();
                return;
            }
            case "openProfile" -> {
                openPage(player, ref, store, pageFactory != null ? pageFactory.createProfilePage(playerRef) : null);
                sendUpdate();
                return;
            }
            case "openBindings" -> {
                openPage(player, ref, store, pageFactory != null ? pageFactory.createRelicBindingsPage(playerRef) : null);
                sendUpdate();
                return;
            }
            case "openSettings" -> {
                openPage(player, ref, store, pageFactory != null ? pageFactory.createSettingsPage(playerRef) : null);
                sendUpdate();
                return;
            }
            case "selectTab" -> selectedTab = HuntCompendiumModel.Tab.fromValue(data.value);
            case "previewPreparation" -> {
                if (data.value != null && !data.value.isBlank()) {
                    previewPreparationId = data.value;
                    selectedTab = HuntCompendiumModel.Tab.PREPARATIONS;
                }
            }
            case "prepare" -> applyPreparationSelection();
            default -> {
            }
        }

        UICommandBuilder cmd = new UICommandBuilder();
        render(cmd, model(store));
        sendUpdate(cmd);
    }

    private void bindEvents(@Nonnull UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabSkillTreeBtn",
                new EventData().append("Action", "openSkillTree"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabProfileBtn",
                new EventData().append("Action", "openProfile"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabRelicBindingsBtn",
                new EventData().append("Action", "openBindings"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#TabSettingsBtn",
                new EventData().append("Action", "openSettings"), false);
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn",
                new EventData().append("Action", "close"), false);
        for (HuntCompendiumModel.Tab tab : HuntCompendiumModel.Tab.values()) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    tabSelector(tab) + " #TabButton",
                    new EventData().append("Action", "selectTab").append("Value", tab.id()),
                    false);
        }
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrepareConfirmBtn",
                new EventData().append("Action", "prepare"), false);
    }

    private void appendPreparationCards(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel model) {
        int maxCards = model.preparationOptions().size();
        for (int i = 0; i < maxCards; i++) {
            cmd.append("#PreparationOptions", VampirismUiPaths.huntCompendiumPreparationCardLayout());
            cmd.setObject("#PreparationOptions[" + i + "].Anchor", createAnchor((i % 3) * 232, (i / 3) * 96, 220, 84));
        }
    }

    private void bindPreparationEvents(@Nonnull UIEventBuilder events, @Nonnull HuntCompendiumModel model) {
        List<HuntCompendiumModel.PreparationOption> options = model.preparationOptions();
        for (int i = 0; i < options.size(); i++) {
            events.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    "#PreparationOptions[" + i + "] #PrepButton",
                    new EventData().append("Action", "previewPreparation").append("Value", options.get(i).preparationId()),
                    false);
        }
    }

    private void render(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel model) {
        cmd.set("#Title.Text", model.title());
        cmd.set("#Subtitle.Text", model.subtitle());
        cmd.set("#PreparedLoadoutValue.Text", model.preparedLoadoutText());
        cmd.set("#NextRankValue.Text", model.nextRankText());
        cmd.set("#FooterHint.Text", model.footerText());

        renderTabs(cmd, model.selectedTab());
        renderOverview(cmd, model);
        renderPreparations(cmd, model);
        renderRecords(cmd, model);
        renderQuarry(cmd, model);
    }

    private void renderTabs(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel.Tab activeTab) {
        for (HuntCompendiumModel.Tab tab : HuntCompendiumModel.Tab.values()) {
            boolean selected = tab == activeTab;
            String selector = tabSelector(tab);
            cmd.set(selector + " #TabLabel.Text", tab.label());
            cmd.set(selector + " #TabBackground.Background.Color", selected ? "#1e3048" : "#0d1820");
            cmd.set(selector + " #TabLabel.Style.TextColor", selected ? "#ffffff" : "#9bb0c2");
        }
        cmd.set("#OverviewPanel.Visible", activeTab == HuntCompendiumModel.Tab.OVERVIEW);
        cmd.set("#PreparationsPanel.Visible", activeTab == HuntCompendiumModel.Tab.PREPARATIONS);
        cmd.set("#RecordsPanel.Visible", activeTab == HuntCompendiumModel.Tab.RECORDS);
        cmd.set("#QuarryPanel.Visible", activeTab == HuntCompendiumModel.Tab.QUARRY);
    }

    private void renderOverview(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel model) {
        cmd.set("#OverviewSummaryText.Text", model.overviewSummaryText());
        cmd.set("#OverviewContinuityText.Text", model.overviewContinuityText());
        cmd.set("#OverviewRewardText.Text", model.overviewRewardText());
    }

    private void renderPreparations(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel model) {
        cmd.set("#PreparationPreviewTitle.Text", model.preparationPreviewTitle());
        cmd.set("#PreparationPreviewStatus.Text", model.preparationPreviewStatus());
        cmd.set("#PreparationPreviewDescription.Text", model.preparationPreviewDescription());
        cmd.set("#PreparationPreviewObjective.Text", model.preparationPreviewObjective());
        cmd.set("#PreparationPreviewEffects.Text", model.preparationPreviewEffects());
        cmd.set("#PrepareConfirmLabel.Text", model.preparationButtonText());

        List<HuntCompendiumModel.PreparationOption> options = model.preparationOptions();
        for (int i = 0; i < options.size(); i++) {
            HuntCompendiumModel.PreparationOption option = options.get(i);
            String selector = "#PreparationOptions[" + i + "]";
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #PrepName.Text", option.displayName());
            cmd.set(selector + " #PrepMode.Text", option.modeDisplayName() + " · " + option.focusLabel());
            cmd.set(selector + " #PrepStatus.Text", option.statusText());
            cmd.set(selector + ".Background.Color", option.previewed()
                    ? "#1e3048"
                    : option.selected() ? "#172733" : "#14202c");
            cmd.set(selector + " #PrepStatus.Style.TextColor", option.selected()
                    ? "#facc15"
                    : option.previewed() ? "#7dd3fc" : "#9bb0c2");
        }
        boolean canApplyPreparation = !model.previewMatchesSelection();
        cmd.set("#PrepareConfirmBtn.HitTestVisible", canApplyPreparation);
        cmd.set("#PrepareConfirmLabel.Style.TextColor", canApplyPreparation ? "#ffffff" : "#8ea2b5");
    }

    private void renderRecords(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel model) {
        cmd.set("#RecordsArchetypesText.Text", model.recordsArchetypeText());
        cmd.set("#RecordsContractsText.Text", model.recordsContractText());
    }

    private void renderQuarry(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel model) {
        cmd.set("#QuarryText.Text", model.quarryText());
    }

    private void applyPreparationSelection() {
        HuntCompendiumModel model = model(null);
        if (model.previewMatchesSelection()) {
            return;
        }
        if (!NightHuntProgressionService.selectPreparation(playerRef.getUuid(), model.previewPreparationId())) {
            return;
        }
        Message message = Message.join(
                Message.raw("Night Hunt").color("dark_red"),
                Message.raw(": ").color("gray"),
                Message.raw("Prepared " + model.preparationPreviewTitle() + " for the next hunt.").color("white"));
        PlayerFeedbackAdapter.sendNotificationWithFallback(playerRef, message, NotificationStyle.Success, message);
    }

    @Nonnull
    private HuntCompendiumModel model(Store<EntityStore> store) {
        MasqueradeHeatSnapshot masquerade = resolveMasqueradeSnapshot();
        HuntCompendiumModel latest = HuntCompendiumModel.create(
                playerRef.getUuid(),
                selectedTab,
                previewPreparationId,
                resolveNextRite(store),
                resolveLineageWindow(masquerade),
                masquerade != null ? MasqueradeHeatThresholdText.compactLine(masquerade, masqueradeHeatService.policy()) : null);
        previewPreparationId = latest.previewPreparationId();
        return latest;
    }

    private HuntCompendiumNextRiteResolver.NextRite resolveNextRite(Store<EntityStore> store) {
        if (nextRiteResolver == null || ritualContextResolver == null || store == null) {
            return null;
        }
        VampiricRitualContext ritualContext = ritualContextResolver.buildContext(playerRef, store, java.util.Set.of());
        return nextRiteResolver.resolve(playerRef.getUuid(), ritualContext);
    }

    private MasqueradeHeatSnapshot resolveMasqueradeSnapshot() {
        if (masqueradeHeatService == null) {
            return null;
        }
        return masqueradeHeatService.snapshot(playerRef.getUuid(), System.currentTimeMillis());
    }

    private LineageWindowOpportunity.View resolveLineageWindow(@Nullable MasqueradeHeatSnapshot masquerade) {
        if (lineageService == null || masquerade == null) {
            return null;
        }
        return LineageWindowOpportunity.resolve(
                masquerade,
                VampirePlayerStateStore.isInitialized()
                        ? VampirePlayerStateStore.get().getBloodAffinities(playerRef.getUuid())
                        : java.util.Map.of(),
                lineageService.evaluateAll(playerRef.getUuid()));
    }

    @Nonnull
    private static String tabSelector(@Nonnull HuntCompendiumModel.Tab tab) {
        return switch (tab) {
            case OVERVIEW -> "#OverviewTab";
            case PREPARATIONS -> "#PreparationsTab";
            case RECORDS -> "#RecordsTab";
            case QUARRY -> "#QuarryTab";
        };
    }

    @Nonnull
    private static Anchor createAnchor(int left, int top, int width, int height) {
        Anchor anchor = new Anchor();
        anchor.setLeft(Value.of(left));
        anchor.setTop(Value.of(top));
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        return anchor;
    }

    private void openPage(@Nullable Player player,
                          @Nonnull Ref<EntityStore> ref,
                          @Nonnull Store<EntityStore> store,
                          @Nullable InteractiveCustomUIPage<?> page) {
        if (player == null || page == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, page);
    }
}
