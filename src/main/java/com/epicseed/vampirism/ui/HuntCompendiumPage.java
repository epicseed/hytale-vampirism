package com.epicseed.vampirism.ui;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.epiccore.hytale.PlayerFeedbackAdapter;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionService;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;
import com.epicseed.vampirism.domain.progression.VampirismProgressionFeaturePolicy;
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

    private static final int OVERVIEW_METRIC_SLOTS = 5;
    private static final int OVERVIEW_STATUS_SLOTS = 8;
    private static final int OVERVIEW_REWARD_SLOTS = 8;
    private static final int PREPARATION_EFFECT_SLOTS = 8;
    private static final int PREPARATION_CARD_WIDTH = 210;
    private static final int PREPARATION_CARD_HEIGHT = 84;
    private static final int PREPARATION_CARD_GAP = 10;
    private static final int RECORD_ARCHETYPE_SLOTS = 8;
    private static final int RECORD_CONTRACT_SLOTS = 10;
    private static final int QUARRY_SLOTS = 12;

    private final ProgressionPageFactory pageFactory;
    private final HuntCompendiumNextRiteResolver nextRiteResolver;
    private final VampiricRitualContextResolver ritualContextResolver;
    private final VampiricLineageService lineageService;
    private final MasqueradeHeatService masqueradeHeatService;
    private final Supplier<? extends VampirismProgressionFeaturePolicy> featurePolicySupplier;
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
        this(playerRef,
                pageFactory,
                ritualService,
                ritualContextResolver,
                lineageService,
                masqueradeHeatService,
                VampirismProgressionFeaturePolicy::allEnabled);
    }

    public HuntCompendiumPage(@Nonnull PlayerRef playerRef,
                              @Nullable ProgressionPageFactory pageFactory,
                              VampiricRitualService ritualService,
                              VampiricRitualContextResolver ritualContextResolver,
                              VampiricLineageService lineageService,
                              MasqueradeHeatService masqueradeHeatService,
                              @Nonnull Supplier<? extends VampirismProgressionFeaturePolicy> featurePolicySupplier) {
        super(playerRef, CustomPageLifetime.CanDismiss, HuntCompendiumEventData.CODEC);
        this.pageFactory = pageFactory;
        this.nextRiteResolver = ritualService != null ? new HuntCompendiumNextRiteResolver(ritualService) : null;
        this.ritualContextResolver = ritualContextResolver;
        this.lineageService = lineageService;
        this.masqueradeHeatService = masqueradeHeatService;
        this.featurePolicySupplier = featurePolicySupplier;
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
        appendDashboardSlots(cmd);
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
            cmd.setObject("#PreparationOptions[" + i + "].Anchor",
                    createHorizontalStackAnchor(PREPARATION_CARD_WIDTH, PREPARATION_CARD_HEIGHT, PREPARATION_CARD_GAP));
        }
    }

    private void appendDashboardSlots(@Nonnull UICommandBuilder cmd) {
        for (int i = 0; i < OVERVIEW_METRIC_SLOTS; i++) {
            cmd.append("#OverviewMetrics", VampirismUiPaths.huntCompendiumMetricCardLayout());
            cmd.setObject("#OverviewMetrics[" + i + "].Anchor", createAnchor(i * 232, 0, 220, 56));
            cmd.set("#OverviewMetrics[" + i + "].Visible", false);
        }
        appendStatusRows(cmd, "#OverviewStatusRows", OVERVIEW_STATUS_SLOTS, 2, 320, 54, 12, 8);
        appendRewardChips(cmd, "#OverviewRewardChips", OVERVIEW_REWARD_SLOTS, 2, 214, 46, 10, 8);
        appendStatusRows(cmd, "#PreparationEffectRows", PREPARATION_EFFECT_SLOTS, 2, 320, 54, 12, 8);
        appendStatusRows(cmd, "#RecordsArchetypeRows", RECORD_ARCHETYPE_SLOTS, 1, 470, 54, 0, 8);
        appendStatusRows(cmd, "#RecordsContractRows", RECORD_CONTRACT_SLOTS, 1, 600, 54, 0, 8);
        for (int i = 0; i < QUARRY_SLOTS; i++) {
            int column = i % 2;
            int row = i / 2;
            cmd.append("#QuarryRows", VampirismUiPaths.huntCompendiumQuarryRowLayout());
            cmd.setObject("#QuarryRows[" + i + "].Anchor", createAnchor(column * 548, row * 84, 520, 76));
            cmd.set("#QuarryRows[" + i + "].Visible", false);
        }
    }

    private void appendStatusRows(@Nonnull UICommandBuilder cmd,
                                  @Nonnull String parentSelector,
                                  int count,
                                  int columns,
                                  int width,
                                  int height,
                                  int columnGap,
                                  int rowGap) {
        for (int i = 0; i < count; i++) {
            int column = i % columns;
            int row = i / columns;
            cmd.append(parentSelector, VampirismUiPaths.huntCompendiumStatusRowLayout());
            cmd.setObject(parentSelector + "[" + i + "].Anchor",
                    createAnchor(column * (width + columnGap), row * (height + rowGap), width, height));
            cmd.set(parentSelector + "[" + i + "].Visible", false);
        }
    }

    private void appendRewardChips(@Nonnull UICommandBuilder cmd,
                                   @Nonnull String parentSelector,
                                   int count,
                                   int columns,
                                   int width,
                                   int height,
                                   int columnGap,
                                   int rowGap) {
        for (int i = 0; i < count; i++) {
            int column = i % columns;
            int row = i / columns;
            cmd.append(parentSelector, VampirismUiPaths.huntCompendiumRewardChipLayout());
            cmd.setObject(parentSelector + "[" + i + "].Anchor",
                    createAnchor(column * (width + columnGap), row * (height + rowGap), width, height));
            cmd.set(parentSelector + "[" + i + "].Visible", false);
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
        renderDominantState(cmd, model.dominantState());
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
        renderMetricCards(cmd, "#OverviewMetrics", model.overviewMetrics(), OVERVIEW_METRIC_SLOTS);
        renderStatusRows(cmd, "#OverviewStatusRows", model.overviewStatusRows(), OVERVIEW_STATUS_SLOTS);
        renderRewardChips(cmd, "#OverviewRewardChips", model.overviewRewardChips(), OVERVIEW_REWARD_SLOTS);
    }

    private void renderDominantState(@Nonnull UICommandBuilder cmd,
                                     @Nonnull HuntCompendiumModel.DominantState state) {
        cmd.set("#DominantIconFrame.Background.Color", state.accentColor());
        cmd.set("#DominantIcon.Text", state.icon());
        cmd.set("#DominantLabel.Text", state.label());
        cmd.set("#DominantValue.Text", state.value());
        cmd.set("#DominantDetail.Text", state.detail());
        cmd.set("#DominantValue.Style.TextColor", state.accentColor());
    }

    private void renderPreparations(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel model) {
        cmd.set("#PreparationPreviewTitle.Text", model.preparationPreviewTitle());
        cmd.set("#PreparationPreviewStatus.Text", model.preparationPreviewStatus());
        cmd.set("#PreparationPreviewDescription.Text", model.preparationPreviewDescription());
        cmd.set("#PreparationPreviewObjective.Text", model.preparationPreviewObjective());
        renderStatusRows(cmd, "#PreparationEffectRows", model.preparationEffectRows(), PREPARATION_EFFECT_SLOTS);
        cmd.set("#PrepareConfirmLabel.Text", model.preparationButtonText());

        List<HuntCompendiumModel.PreparationOption> options = model.preparationOptions();
        for (int i = 0; i < options.size(); i++) {
            HuntCompendiumModel.PreparationOption option = options.get(i);
            String selector = "#PreparationOptions[" + i + "]";
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #PrepAccent.Background.Color", option.accentColor());
            cmd.set(selector + " #PrepIconFrame.Background.Color", option.accentColor());
            cmd.set(selector + " #PrepIcon.Text", option.icon());
            cmd.set(selector + " #PrepBadge.Background.Color", option.accentColor());
            cmd.set(selector + " #PrepBadgeText.Text", option.previewed()
                    ? option.selected() ? "Ready" : "Preview"
                    : option.selected() ? "Ready" : "Route");
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
        renderStatusRows(cmd, "#RecordsArchetypeRows", model.recordsArchetypeRows(), RECORD_ARCHETYPE_SLOTS);
        renderStatusRows(cmd, "#RecordsContractRows", model.recordsContractRows(), RECORD_CONTRACT_SLOTS);
    }

    private void renderQuarry(@Nonnull UICommandBuilder cmd, @Nonnull HuntCompendiumModel model) {
        renderQuarryRows(cmd, model.quarryRows(), QUARRY_SLOTS);
    }

    private void renderMetricCards(@Nonnull UICommandBuilder cmd,
                                   @Nonnull String parentSelector,
                                   @Nonnull List<HuntCompendiumModel.DashboardMetric> metrics,
                                   int slots) {
        for (int i = 0; i < slots; i++) {
            String selector = parentSelector + "[" + i + "]";
            if (i >= metrics.size()) {
                cmd.set(selector + ".Visible", false);
                continue;
            }
            HuntCompendiumModel.DashboardMetric metric = metrics.get(i);
            boolean hasIcon = !metric.icon().isBlank();
            boolean hasState = !metric.stateLabel().isBlank();
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #MetricAccent.Background.Color", metric.accentColor());
            cmd.set(selector + " #MetricIconFrame.Visible", hasIcon);
            cmd.set(selector + " #MetricIconFrame.Background.Color", metric.severityColor());
            cmd.set(selector + " #MetricIcon.Text", metric.icon());
            cmd.set(selector + " #MetricStateBadge.Visible", hasState);
            cmd.set(selector + " #MetricStateBadge.Background.Color", metric.severityColor());
            cmd.set(selector + " #MetricStateLabel.Text", metric.stateLabel());
            cmd.set(selector + " #MetricLabel.Text", metric.label());
            cmd.set(selector + " #MetricValue.Text", metric.value());
            cmd.set(selector + " #MetricDetail.Text", metric.detail());
        }
    }

    private void renderStatusRows(@Nonnull UICommandBuilder cmd,
                                  @Nonnull String parentSelector,
                                  @Nonnull List<HuntCompendiumModel.DashboardRow> rows,
                                  int slots) {
        for (int i = 0; i < slots; i++) {
            String selector = parentSelector + "[" + i + "]";
            if (i >= rows.size()) {
                cmd.set(selector + ".Visible", false);
                continue;
            }
            HuntCompendiumModel.DashboardRow row = rows.get(i);
            boolean hasIcon = !row.icon().isBlank();
            boolean hasState = !row.stateLabel().isBlank();
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #StatusAccent.Background.Color", row.accentColor());
            cmd.set(selector + " #StatusIconFrame.Visible", hasIcon);
            cmd.set(selector + " #StatusIconFrame.Background.Color", row.severityColor());
            cmd.set(selector + " #StatusIcon.Text", row.icon());
            cmd.set(selector + " #StatusStateBadge.Visible", hasState);
            cmd.set(selector + " #StatusStateBadge.Background.Color", row.severityColor());
            cmd.set(selector + " #StatusStateLabel.Text", row.stateLabel());
            cmd.set(selector + " #StatusLabel.Text", row.label());
            cmd.set(selector + " #StatusValue.Text", row.value());
            cmd.set(selector + " #StatusDetail.Text", row.detail());
        }
    }

    private void renderRewardChips(@Nonnull UICommandBuilder cmd,
                                   @Nonnull String parentSelector,
                                   @Nonnull List<HuntCompendiumModel.RewardChip> chips,
                                   int slots) {
        for (int i = 0; i < slots; i++) {
            String selector = parentSelector + "[" + i + "]";
            if (i >= chips.size()) {
                cmd.set(selector + ".Visible", false);
                continue;
            }
            HuntCompendiumModel.RewardChip chip = chips.get(i);
            boolean hasIcon = !chip.icon().isBlank();
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #RewardAccent.Background.Color", chip.accentColor());
            cmd.set(selector + " #RewardIconFrame.Visible", hasIcon);
            cmd.set(selector + " #RewardIconFrame.Background.Color", chip.accentColor());
            cmd.set(selector + " #RewardIcon.Text", chip.icon());
            cmd.set(selector + " #RewardLabel.Text", chip.label());
            cmd.set(selector + " #RewardValue.Text", chip.value());
        }
    }

    private void renderQuarryRows(@Nonnull UICommandBuilder cmd,
                                  @Nonnull List<HuntCompendiumModel.QuarryRow> rows,
                                  int slots) {
        for (int i = 0; i < slots; i++) {
            String selector = "#QuarryRows[" + i + "]";
            if (i >= rows.size()) {
                cmd.set(selector + ".Visible", false);
                continue;
            }
            HuntCompendiumModel.QuarryRow row = rows.get(i);
            cmd.set(selector + ".Visible", true);
            cmd.set(selector + " #QuarryAccent.Background.Color", row.accentColor());
            cmd.set(selector + " #QuarryIconFrame.Background.Color", row.accentColor());
            cmd.set(selector + " #QuarryIcon.Text", row.icon());
            cmd.set(selector + " #QuarryTierBadge.Background.Color", row.accentColor());
            cmd.set(selector + " #QuarryTierText.Text", row.tierBadge());
            cmd.set(selector + " #QuarryStateBadge.Background.Color", row.accentColor());
            cmd.set(selector + " #QuarryStateText.Text", row.stateLabel());
            cmd.set(selector + " #QuarryName.Text", row.name());
            cmd.set(selector + " #QuarryTags.Text", row.tags());
            cmd.set(selector + " #QuarryStatus.Text", row.status());
        }
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
                masquerade != null ? MasqueradeHeatThresholdText.compactLine(masquerade, masqueradeHeatService.policy()) : null,
                featurePolicySupplier.get());
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

    @Nonnull
    private static Anchor createHorizontalStackAnchor(int width, int height, int rightGap) {
        Anchor anchor = new Anchor();
        anchor.setWidth(Value.of(width));
        anchor.setHeight(Value.of(height));
        anchor.setRight(Value.of(rightGap));
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
