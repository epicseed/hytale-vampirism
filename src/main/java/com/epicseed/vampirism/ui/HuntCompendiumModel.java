package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.vampirism.domain.age.VampiricAgeTierSnapshot;
import com.epicseed.epiccore.vampirism.domain.player.PersistedNightHuntState;
import com.epicseed.epiccore.vampirism.domain.player.NamedHuntProgress;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.age.VampiricAgeTierService;
import com.epicseed.vampirism.domain.hunt.NightHuntCasefileService;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuityService;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntContracts;
import com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout;
import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionService;
import com.epicseed.vampirism.domain.progression.VampirismProgressionFeaturePolicy;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;

final class HuntCompendiumModel {

    enum Tab {
        OVERVIEW("overview", "Overview"),
        PREPARATIONS("preparations", "Preparations"),
        RECORDS("records", "Records"),
        QUARRY("quarry", "Quarry");

        private final String id;
        private final String label;

        Tab(@Nonnull String id, @Nonnull String label) {
            this.id = id;
            this.label = label;
        }

        @Nonnull
        String id() {
            return id;
        }

        @Nonnull
        String label() {
            return label;
        }

        @Nonnull
        static Tab fromValue(@Nullable String value) {
            if (value == null || value.isBlank()) {
                return OVERVIEW;
            }
            for (Tab tab : values()) {
                if (tab.id.equalsIgnoreCase(value.trim())) {
                    return tab;
                }
            }
            return OVERVIEW;
        }
    }

    record PreparationOption(@Nonnull String preparationId,
                             @Nonnull String displayName,
                             @Nonnull String modeDisplayName,
                             @Nonnull String focusLabel,
                             @Nonnull String statusText,
                             @Nonnull String icon,
                             @Nonnull String accentColor,
                             boolean selected,
                             boolean previewed) {
    }

    record DominantState(@Nonnull String icon,
                        @Nonnull String label,
                        @Nonnull String value,
                        @Nonnull String detail,
                        @Nonnull String accentColor) {
    }

    record DashboardMetric(@Nonnull String label,
                           @Nonnull String value,
                           @Nonnull String detail,
                           @Nonnull String accentColor,
                           @Nonnull String icon,
                           @Nonnull String stateLabel,
                           @Nonnull String severityColor) {
        DashboardMetric(@Nonnull String label,
                       @Nonnull String value,
                       @Nonnull String detail,
                       @Nonnull String accentColor) {
            this(label, value, detail, accentColor, "", "", accentColor);
        }
    }

    record DashboardRow(@Nonnull String label,
                       @Nonnull String value,
                       @Nonnull String detail,
                       @Nonnull String accentColor,
                       @Nonnull String icon,
                       @Nonnull String stateLabel,
                       @Nonnull String severityColor) {
        DashboardRow(@Nonnull String label,
                     @Nonnull String value,
                     @Nonnull String detail,
                     @Nonnull String accentColor) {
            this(label, value, detail, accentColor, "", "", accentColor);
        }
    }

    record RewardChip(@Nonnull String label,
                      @Nonnull String value,
                      @Nonnull String accentColor,
                      @Nonnull String icon) {
        RewardChip(@Nonnull String label,
                   @Nonnull String value,
                   @Nonnull String accentColor) {
            this(label, value, accentColor, "");
        }
    }

    record QuarryRow(@Nonnull String name,
                     @Nonnull String tags,
                     @Nonnull String status,
                     @Nonnull String accentColor,
                     @Nonnull String icon,
                     @Nonnull String tierBadge,
                     @Nonnull String stateLabel) {
    }

    private final NightHuntMasterySnapshot mastery;
    private final NamedHuntProgress progress;
    private final List<NightHuntSpawnRegistry.SpawnOption> preyCatalogue;
    private final List<NightHuntPreparedLoadout> availableLoadouts;
    private final NightHuntPreparedLoadout currentLoadout;
    private final NightHuntPreparedLoadout previewLoadout;
    private final PersistedNightHuntState persistedNightHuntState;
    private final NightHuntContinuitySnapshot continuity;
    private final HuntCompendiumNextRiteResolver.NextRite nextRite;
    private final LineageWindowOpportunity.View lineageWindow;
    private final VampiricAgeTierSnapshot ageTierSnapshot;
    private final String nextThresholdText;
    private final Tab selectedTab;
    private final VampirismProgressionFeaturePolicy featurePolicy;

    private HuntCompendiumModel(@Nonnull NightHuntMasterySnapshot mastery,
                                @Nonnull NamedHuntProgress progress,
                                @Nonnull List<NightHuntSpawnRegistry.SpawnOption> preyCatalogue,
                                @Nonnull List<NightHuntPreparedLoadout> availableLoadouts,
                                     @Nonnull NightHuntPreparedLoadout currentLoadout,
                                     @Nonnull NightHuntPreparedLoadout previewLoadout,
                                     @Nonnull PersistedNightHuntState persistedNightHuntState,
                                     @Nonnull NightHuntContinuitySnapshot continuity,
                                     @Nullable HuntCompendiumNextRiteResolver.NextRite nextRite,
                                     @Nullable LineageWindowOpportunity.View lineageWindow,
                                     @Nonnull VampiricAgeTierSnapshot ageTierSnapshot,
                                    @Nullable String nextThresholdText,
                                    @Nonnull Tab selectedTab,
                                    @Nonnull VampirismProgressionFeaturePolicy featurePolicy) {
        this.mastery = mastery;
        this.progress = progress;
        this.preyCatalogue = preyCatalogue;
        this.availableLoadouts = availableLoadouts;
        this.currentLoadout = currentLoadout;
        this.previewLoadout = previewLoadout;
        this.persistedNightHuntState = persistedNightHuntState;
        this.continuity = continuity;
        this.nextRite = nextRite;
        this.lineageWindow = lineageWindow;
        this.ageTierSnapshot = ageTierSnapshot;
        this.nextThresholdText = nextThresholdText;
        this.selectedTab = selectedTab;
        this.featurePolicy = featurePolicy;
    }

    @Nonnull
    static HuntCompendiumModel create(@Nonnull UUID uuid,
                                      @Nonnull Tab selectedTab,
                                      @Nullable String previewPreparationId,
                                      @Nullable HuntCompendiumNextRiteResolver.NextRite nextRite,
                                      @Nullable LineageWindowOpportunity.View lineageWindow,
                                      @Nullable String nextThresholdText) {
        return create(uuid,
                selectedTab,
                previewPreparationId,
                nextRite,
                lineageWindow,
                nextThresholdText,
                VampirismProgressionFeaturePolicy.allEnabled());
    }

    @Nonnull
    static HuntCompendiumModel create(@Nonnull UUID uuid,
                                      @Nonnull Tab selectedTab,
                                      @Nullable String previewPreparationId,
                                      @Nullable HuntCompendiumNextRiteResolver.NextRite nextRite,
                                      @Nullable LineageWindowOpportunity.View lineageWindow,
                                      @Nullable String nextThresholdText,
                                      @Nonnull VampirismProgressionFeaturePolicy featurePolicy) {
        NamedHuntProgress progress = VampirePlayerStateStore.get().getNamedHuntProgress(uuid, NightHuntContracts.HUNT_ID);
        List<NightHuntSpawnRegistry.SpawnOption> preyCatalogue = new ArrayList<>(NightHuntSpawnRegistry.get().allSpawns());
        preyCatalogue.sort(Comparator
                .comparing((NightHuntSpawnRegistry.SpawnOption option) -> !progress.discoveredPreyRoleIds.contains(option.roleId()))
                .thenComparing(NightHuntSpawnRegistry.SpawnOption::elite, Comparator.reverseOrder())
                .thenComparing(NightHuntSpawnRegistry.SpawnOption::visualTier, Comparator.reverseOrder())
                .thenComparing(NightHuntSpawnRegistry.SpawnOption::displayName, String.CASE_INSENSITIVE_ORDER));
        List<NightHuntPreparedLoadout> availableLoadouts = NightHuntProgressionService.availablePreparedLoadouts();
        NightHuntPreparedLoadout currentLoadout = NightHuntProgressionService.preparedLoadout(progress);
        NightHuntPreparedLoadout previewLoadout = resolvePreviewLoadout(availableLoadouts, currentLoadout, previewPreparationId);
        NightHuntContinuitySnapshot continuity = NightHuntContinuityService.snapshot(progress);
        return new HuntCompendiumModel(
                NightHuntProgressionService.snapshot(progress),
                progress,
                List.copyOf(preyCatalogue),
                List.copyOf(availableLoadouts),
                currentLoadout,
                previewLoadout,
                VampirePlayerStateStore.get().getPersistedNightHuntState(uuid),
                continuity,
                nextRite,
                lineageWindow,
                VampiricAgeTierService.snapshot(uuid),
                nextThresholdText,
                selectedTab,
                featurePolicy);
    }

    @Nonnull
    String title() {
        return "Night Hunt Briefing";
    }

    @Nonnull
    String subtitle() {
        String preyText = mastery.discoveredPreyRoleIds().size() + "/" + preyCatalogue.size() + " prey logged";
        return featurePolicy.nightHuntProgressionEnabled()
                ? mastery.currentRank().displayName() + " · " + mastery.masteryPoints() + " mastery · " + preyText
                : preyText;
    }

    @Nonnull
    String preparedLoadoutText() {
        NightHuntPreparationAffinityContent.PreparationAffinity focus =
                NightHuntPreparationAffinityContent.focusForPreparation(currentLoadout.preparationId());
        return currentLoadout.preparationDisplayName() + " · " + currentLoadout.modeDisplayName()
                + (featurePolicy.bloodAffinityProgressionEnabled() && focus != null ? " · " + focus.laneLabel() : "");
    }

    @Nonnull
    String nextRankText() {
        if (!featurePolicy.nightHuntProgressionEnabled()) {
            return "Mastery progression disabled";
        }
        return mastery.nextRank() != null
                ? mastery.nextRank().displayName() + " in " + mastery.masteryToNextRank()
                : "Maximum rank claimed";
    }

    @Nonnull
    DominantState dominantState() {
        HuntCrackdownText.View crackdown = HuntCrackdownText.resolve(persistedNightHuntState, continuity, progress);
        HuntCasefileText.View casefile = HuntCasefileText.resolve(persistedNightHuntState, progress);
        if (persistedNightHuntState.cooldownRemainingMs(System.currentTimeMillis()) > 0L || crackdown.active()) {
            return new DominantState(
                    VampirismVisuals.ICON_THREAT,
                    "Hunt window",
                    crackdown.value(),
                    compactDetail(crackdown.detail()),
                    crackdown.accentColor());
        }
        if (casefile.active()) {
            return new DominantState(
                    VampirismVisuals.ICON_RECORD,
                    "Casefile",
                    casefile.value(),
                    compactDetail(casefile.detail()),
                    casefile.escalated() ? VampirismVisuals.WARNING : VampirismVisuals.INFO);
        }
        if (featurePolicy.nightHuntProgressionEnabled()
                && mastery.nextRank() != null
                && mastery.masteryToNextRank() <= 5) {
            return new DominantState(
                    VampirismVisuals.ICON_REWARD,
                    "Near mastery",
                    mastery.nextRank().displayName(),
                    mastery.masteryToNextRank() + " mastery to next rank",
                    VampirismVisuals.WARNING);
        }
        return new DominantState(
                VampirismVisuals.ICON_READY,
                "Prepared",
                currentLoadout.preparationDisplayName(),
                currentLoadout.modeDisplayName()
                        + (featurePolicy.bloodAffinityProgressionEnabled()
                        ? " · " + preparationFocusLabel(currentLoadout.preparationId())
                        : ""),
                VampirismVisuals.SAFE);
    }

    @Nonnull
    String overviewSummaryText() {
        return joinMetrics(overviewMetrics());
    }

    @Nonnull
    String overviewContinuityText() {
        return joinDashboardRows(overviewStatusRows());
    }

    @Nonnull
    List<DashboardMetric> overviewMetrics() {
        ArrayList<DashboardMetric> metrics = new ArrayList<>();
        if (featurePolicy.nightHuntProgressionEnabled()) {
            metrics.add(new DashboardMetric(
                        "Rank",
                        mastery.currentRank().displayName(),
                        mastery.masteryPoints() + " mastery",
                        mastery.currentRank().accentColor(),
                        VampirismVisuals.ICON_HUNT,
                        "Rank",
                        mastery.currentRank().accentColor()));
        }
        metrics.add(new DashboardMetric(
                        "Prey logged",
                        mastery.discoveredPreyRoleIds().size() + "/" + preyCatalogue.size(),
                        "Known quarry",
                        "#ef4444",
                        VampirismVisuals.ICON_PREY,
                        "Quarry",
                        VampirismVisuals.DANGER));
        metrics.add(new DashboardMetric(
                        "Contracts",
                        Integer.toString(mastery.totalCompletions()),
                        mastery.uniqueContractsCompleted() + " unique",
                        "#dc2626",
                        VampirismVisuals.ICON_RECORD,
                        "Ledger",
                        VampirismVisuals.DANGER));
        metrics.add(new DashboardMetric(
                        "Elite claims",
                        Integer.toString(mastery.eliteCompletionCount()),
                        "High-value prey",
                        "#f59e0b",
                        VampirismVisuals.ICON_THREAT,
                        "Elite",
                        VampirismVisuals.WARNING));
        if (featurePolicy.nightHuntProgressionEnabled()) {
            metrics.add(new DashboardMetric(
                        "Next rank",
                        mastery.nextRank() != null ? mastery.nextRank().displayName() : "Max",
                        mastery.nextRank() != null ? mastery.masteryToNextRank() + " mastery left" : "All ranks claimed",
                        "#facc15",
                        VampirismVisuals.ICON_REWARD,
                        mastery.nextRank() != null ? "Goal" : "Max",
                        mastery.nextRank() != null ? VampirismVisuals.WARNING : VampirismVisuals.SPECIAL));
        }
        return List.copyOf(metrics);
    }

    @Nonnull
    List<DashboardRow> overviewStatusRows() {
        ArrayList<DashboardRow> rows = new ArrayList<>();
        HuntCrackdownText.View crackdown = HuntCrackdownText.resolve(persistedNightHuntState, continuity, progress);
        HuntCasefileText.View casefile = HuntCasefileText.resolve(persistedNightHuntState, progress);
        if (persistedNightHuntState.cooldownRemainingMs(System.currentTimeMillis()) > 0L || crackdown.active()) {
            rows.add(new DashboardRow(
                    "Next window",
                    crackdown.value(),
                    compactDetail(crackdown.detail()),
                    crackdown.accentColor(),
                    VampirismVisuals.ICON_THREAT,
                    crackdown.active() ? "Active" : "Timer",
                    crackdown.accentColor()));
        }
        if (casefile.active()) {
            rows.add(new DashboardRow(
                    "Casefile",
                    casefile.value(),
                    compactDetail(casefile.detail()),
                    casefile.escalated() ? "#f97316" : "#f59e0b",
                    VampirismVisuals.ICON_RECORD,
                    casefile.escalated() ? "Hot" : "Open",
                    casefile.escalated() ? VampirismVisuals.WARNING : VampirismVisuals.INFO));
        } else {
            String lastClearedCasefile = NightHuntCasefileService.lastClearedCasefileDisplayName(progress);
            if (lastClearedCasefile != null) {
                rows.add(new DashboardRow(
                        "Casefile",
                        lastClearedCasefile,
                        "Hunters are pivoting away from that route",
                        "#22c55e",
                        VampirismVisuals.ICON_READY,
                        "Clear",
                        VampirismVisuals.SAFE));
            }
        }
        rows.add(new DashboardRow(
                "Threat",
                continuity.worldThreatLevel() > 0 ? continuity.worldThreatName() : "Quiet",
                continuity.worldThreatLevel() > 0 ? "Level " + continuity.worldThreatLevel() : "No active world pressure",
                continuity.worldThreatLevel() > 0 ? "#f59e0b" : "#22c55e",
                VampirismVisuals.ICON_THREAT,
                continuity.worldThreatLevel() > 0 ? "Alert" : "Quiet",
                continuity.worldThreatLevel() > 0 ? VampirismVisuals.WARNING : VampirismVisuals.SAFE));
        if (continuity.preyMemoryName() != null && continuity.preyMemoryLevel() > 0) {
            rows.add(new DashboardRow(
                    "Prey memory",
                    continuity.preyMemoryName(),
                    "Level " + continuity.preyMemoryLevel(),
                    "#f97316",
                    VampirismVisuals.ICON_PREY,
                    "Memory",
                    VampirismVisuals.WARNING));
        }
        if (continuity.behaviorMemoryName() != null && continuity.behaviorMemoryLevel() > 0) {
            rows.add(new DashboardRow(
                    "Behavior read",
                    continuity.behaviorMemoryName(),
                    "Level " + continuity.behaviorMemoryLevel(),
                    "#f59e0b",
                    VampirismVisuals.ICON_RECORD,
                    "Read",
                    VampirismVisuals.WARNING));
        }
        if (continuity.activeChainName() != null && continuity.activeChainStep() > 0) {
            rows.add(new DashboardRow(
                    "Chain",
                    continuity.activeChainName() + " " + roman(continuity.activeChainStep()),
                    "Active escalation",
                    "#c084fc",
                    VampirismVisuals.ICON_SPECIAL,
                    "Chain",
                    VampirismVisuals.SPECIAL));
        }
        rows.add(new DashboardRow(
                "Streak",
                continuity.successStreak() + " / " + continuity.failureStreak(),
                "success / failure",
                continuity.failureStreak() > continuity.successStreak() ? "#f97316" : "#22c55e",
                VampirismVisuals.ICON_RECORD,
                "Run",
                continuity.failureStreak() > continuity.successStreak() ? VampirismVisuals.WARNING : VampirismVisuals.SAFE));
        return List.copyOf(rows);
    }

    @Nonnull
    String overviewRewardText() {
        return joinChips(overviewRewardChips()) + overviewGuidanceText(
                nextRite,
                lineageWindow,
                featurePolicy.ageTierProgressionEnabled() ? ageTierSnapshot : null,
                nextThresholdText);
    }

    @Nonnull
    List<RewardChip> overviewRewardChips() {
        if (mastery.lastRewardedAtMs() <= 0L) {
            return List.of(new RewardChip("Recent reward", "None yet", "#64748b", VampirismVisuals.ICON_UNKNOWN));
        }
        ArrayList<RewardChip> chips = new ArrayList<>();
        String source = mastery.lastRewardedPreyRoleId() != null
                ? NightHuntPresentationText.preyName(mastery.lastRewardedPreyRoleId())
                : mastery.lastRewardedContractId() != null
                ? NightHuntPresentationText.contractTargetSummary(mastery.lastRewardedContractId())
                : "Unknown prey";
        chips.add(new RewardChip("Last prey", source, "#ef4444", VampirismVisuals.ICON_PREY));
        if (mastery.lastRewardSkillPoints() > 0) {
            chips.add(new RewardChip("Skill", "+" + mastery.lastRewardSkillPoints(), "#facc15", VampirismVisuals.ICON_REWARD));
        }
        if (featurePolicy.nightHuntProgressionEnabled() && mastery.lastRewardMasteryPoints() > 0) {
            chips.add(new RewardChip("Mastery", "+" + mastery.lastRewardMasteryPoints(), "#f59e0b", VampirismVisuals.ICON_HUNT));
        }
        if (mastery.lastRewardBlood() > 0) {
            chips.add(new RewardChip("Blood", "+" + mastery.lastRewardBlood(), "#dc2626", VampirismVisuals.ICON_HEAT));
        }
        if (featurePolicy.ageTierProgressionEnabled() && mastery.lastRewardAgeProgress() > 0) {
            chips.add(new RewardChip("Age", "+" + mastery.lastRewardAgeProgress(), "#c084fc", VampirismVisuals.ICON_AGE));
        }
        if (featurePolicy.bloodAffinityProgressionEnabled()
                && mastery.lastRewardAffinityAmount() > 0
                && mastery.lastRewardAffinityId() != null) {
            chips.add(new RewardChip(
                    NightHuntPresentationText.humanize(mastery.lastRewardAffinityId()),
                    "+" + mastery.lastRewardAffinityAmount(),
                    "#22c55e",
                    VampirismVisuals.ICON_READY));
        }
        if (mastery.lastRewardedArchetypeMilestoneId() != null) {
            chips.add(new RewardChip(
                    "Milestone",
                    NightHuntPresentationText.humanize(mastery.lastRewardedArchetypeMilestoneId()),
                    "#7dd3fc",
                    VampirismVisuals.ICON_SPECIAL));
        }
        if (progress.lastOutcomeId != null) {
            chips.add(new RewardChip("Outcome", NightHuntPresentationText.humanize(progress.lastOutcomeId), "#9bb0c2", VampirismVisuals.ICON_RECORD));
        }
        return List.copyOf(chips);
    }

    @Nonnull
    static String overviewGuidanceText(@Nullable HuntCompendiumNextRiteResolver.NextRite nextRite,
                                       @Nullable LineageWindowOpportunity.View lineageWindow,
                                       @Nullable VampiricAgeTierSnapshot ageTierSnapshot,
                                       @Nullable String nextThresholdText) {
        ArrayList<String> downstreamLines = new ArrayList<>();
        if (lineageWindow != null) {
            downstreamLines.add(lineageWindow.compactText());
        }
        if (ageTierSnapshot != null) {
            String nextRiseText = VampiricAgeTierProgressionText.compactNextRiseLine(ageTierSnapshot);
            if (!nextRiseText.isEmpty()) {
                downstreamLines.add(nextRiseText);
            }
        }
        String compactThresholdText = nextThresholdText != null ? nextThresholdText.trim() : "";
        if (!compactThresholdText.isEmpty()) {
            downstreamLines.add(compactThresholdText);
        }
        if (nextRite != null) {
            return "\n\nNext rite: " + nextRite.ritualName()
                    + "\n" + nextRite.guidance()
                    + (downstreamLines.isEmpty() ? "" : "\n" + String.join("\n", downstreamLines));
        }
        String downstreamText = String.join("\n", downstreamLines);
        return downstreamText.isEmpty() ? "" : "\n\n" + downstreamText;
    }

    @Nonnull
    List<PreparationOption> preparationOptions() {
        ArrayList<PreparationOption> options = new ArrayList<>();
        for (NightHuntPreparedLoadout loadout : availableLoadouts) {
            boolean selected = loadout.preparationId().equals(currentLoadout.preparationId());
            boolean previewed = loadout.preparationId().equals(previewLoadout.preparationId());
            String status = previewed
                    ? selected ? "Prepared now" : "Previewing"
                    : selected ? "Prepared now" : "Available";
            options.add(new PreparationOption(
                    loadout.preparationId(),
                    loadout.preparationDisplayName(),
                    loadout.modeDisplayName(),
                    featurePolicy.bloodAffinityProgressionEnabled()
                            ? preparationFocusLabel(loadout.preparationId())
                            : "Open focus",
                    status,
                    preparationIcon(loadout.preparationId()),
                    selected ? VampirismVisuals.WARNING : previewed ? VampirismVisuals.INFO : VampirismVisuals.NEUTRAL,
                    selected,
                    previewed));
        }
        return List.copyOf(options);
    }

    @Nonnull
    String preparationPreviewTitle() {
        return previewLoadout.preparationDisplayName() + " · " + previewLoadout.modeDisplayName();
    }

    @Nonnull
    String preparationPreviewStatus() {
        return previewLoadout.preparationId().equals(currentLoadout.preparationId())
                ? "Prepared for the next Night Hunt."
                : "Previewing a different loadout for your next Night Hunt.";
    }

    @Nonnull
    String preparationPreviewDescription() {
        return previewLoadout.preparationDescription();
    }

    @Nonnull
    String preparationPreviewObjective() {
        return previewLoadout.objectiveText();
    }

    @Nonnull
    String preparationPreviewEffects() {
        return joinDashboardRows(preparationEffectRows());
    }

    @Nonnull
    List<DashboardRow> preparationEffectRows() {
        ArrayList<DashboardRow> rows = new ArrayList<>();
        NightHuntPreparationAffinityContent.PreparationAffinity focus =
                NightHuntPreparationAffinityContent.focusForPreparation(previewLoadout.preparationId());
        if (featurePolicy.bloodAffinityProgressionEnabled() && focus != null) {
            rows.add(new DashboardRow("Affinity", focus.preyFamilyDisplayName(), compactDetail(focus.bonusText()), "#22c55e"));
            rows.add(new DashboardRow("Path", focus.laneLabel(), compactDetail(focus.focusText()), "#7dd3fc"));
        } else if (featurePolicy.bloodAffinityProgressionEnabled()) {
            rows.add(new DashboardRow("Affinity", "None", "No dedicated prey-family lane", "#64748b"));
        }
        rows.add(new DashboardRow("Trail tier", signedDelta(previewLoadout.visualTierDelta()), "visual trail delta", deltaAccent(previewLoadout.visualTierDelta())));
        rows.add(new DashboardRow("Waypoints", signedDelta(previewLoadout.waypointTargetAdjustment()), "route length delta", deltaAccent(-previewLoadout.waypointTargetAdjustment())));
        rows.add(new DashboardRow("Lifetime", "x" + trimMultiplier(previewLoadout.preyLifetimeMultiplier()), "prey uptime", "#f59e0b"));
        rows.add(new DashboardRow("Prime", previewLoadout.requiredOwnerHits() > 0 ? previewLoadout.requiredOwnerHits() + " hits" : "None", "owner-hit requirement", previewLoadout.requiredOwnerHits() > 0 ? "#f97316" : "#22c55e"));
        rows.add(new DashboardRow("Pressure", previewLoadout.pressureSeconds() > 0f ? Math.round(previewLoadout.pressureSeconds()) + "s" : "None", previewLoadout.pressureSeconds() > 0f ? "within " + trimRadius(previewLoadout.pressureRadius()) + "m" : "no hold", previewLoadout.pressureSeconds() > 0f ? "#c084fc" : "#22c55e"));
        return List.copyOf(rows);
    }

    boolean previewMatchesSelection() {
        return previewLoadout.preparationId().equals(currentLoadout.preparationId());
    }

    @Nonnull
    String preparationButtonText() {
        return previewMatchesSelection() ? "Prepared" : "Prepare for next hunt";
    }

    @Nonnull
    String recordsArchetypeText() {
        return joinDashboardRows(recordsArchetypeRows());
    }

    @Nonnull
    List<DashboardRow> recordsArchetypeRows() {
        NightHuntProgressionRegistry.Snapshot registry = NightHuntProgressionRegistry.get().snapshot();
        ArrayList<DashboardRow> rows = new ArrayList<>();
        for (NightHuntProgressionRegistry.ArchetypeDefinition archetype : registry.archetypes()) {
            int completions = mastery.archetypeCompletionCounts().getOrDefault(archetype.id(), 0);
            NightHuntProgressionRegistry.ArchetypeMilestone achieved = archetype.achievedMilestone(completions);
            NightHuntProgressionRegistry.ArchetypeMilestone next = archetype.nextMilestone(completions);
            String value = completions + " claim" + (completions == 1 ? "" : "s");
            String detail = next != null
                    ? "next in " + Math.max(0, next.killsRequired() - completions)
                    : "mastered";
            if (achieved != null) {
                detail = achieved.displayName() + " · " + detail;
            }
            rows.add(new DashboardRow(archetype.displayName(), value, detail, next == null ? "#22c55e" : "#f59e0b"));
        }
        return List.copyOf(rows);
    }

    @Nonnull
    String recordsContractText() {
        return joinDashboardRows(recordsContractRows());
    }

    @Nonnull
    List<DashboardRow> recordsContractRows() {
        ArrayList<Map.Entry<String, Integer>> entries = new ArrayList<>(progress.contractCompletionCounts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        ArrayList<DashboardRow> rows = new ArrayList<>();
        if (progress.activeContractId != null) {
            rows.add(new DashboardRow("Active", NightHuntPresentationText.contractTargetSummary(progress.activeContractId), "stage " + Math.max(1, progress.activeStep), "#facc15"));
        }
        if (progress.activeChainId != null && progress.activeChainStep > 0) {
            rows.add(new DashboardRow("Chain", NightHuntPresentationText.humanize(progress.activeChainId), roman(progress.activeChainStep), "#c084fc"));
        }
        if (entries.isEmpty()) {
            rows.add(new DashboardRow("Completed", "None yet", "Finish hunts to fill the ledger", "#64748b"));
        } else {
            for (int i = 0; i < Math.min(entries.size(), 8); i++) {
                Map.Entry<String, Integer> entry = entries.get(i);
                rows.add(new DashboardRow(
                        "Completed",
                        NightHuntPresentationText.contractTargetSummary(entry.getKey()),
                        entry.getValue() + " completion" + (entry.getValue() == 1 ? "" : "s"),
                        "#ef4444"));
            }
        }
        return List.copyOf(rows);
    }

    @Nonnull
    static String preparationFocusLabel(@Nullable String preparationId) {
        NightHuntPreparationAffinityContent.PreparationAffinity focus =
                NightHuntPreparationAffinityContent.focusForPreparation(preparationId);
        return focus != null ? focus.laneLabel() : "Open focus";
    }

    @Nonnull
    static String preparationRecapText(@Nonnull NightHuntPreparedLoadout loadout) {
        NightHuntPreparationAffinityContent.PreparationAffinity focus =
                NightHuntPreparationAffinityContent.focusForPreparation(loadout.preparationId());
        if (focus == null) {
            return "No dedicated prey-family lane is authored for this preparation.";
        }
        ArrayList<String> parts = new ArrayList<>();
        parts.add(focus.laneLabel());
        String bonus = matchingBonusText(loadout.affinityFocusMasteryBonus(), loadout.affinityFocusBloodBonus(), focus.preyFamilyDisplayName());
        if (!bonus.isBlank()) {
            parts.add(bonus);
        }
        String signaturePreview = signaturePreviewText(loadout, focus);
        if (!signaturePreview.isBlank()) {
            parts.add(signaturePreview);
        }
        parts.add(focus.bonusText());
        return String.join(" · ", parts);
    }

    @Nonnull
    static String recentRewardLaneText(@Nonnull NightHuntMasterySnapshot mastery) {
        if (mastery.lastRewardPreparationId() != null && mastery.lastRewardPreparationAffinityFocusId() != null) {
            NightHuntPreparationAffinityContent.PreparationAffinity focus =
                    NightHuntPreparationAffinityContent.focusForPreparation(mastery.lastRewardPreparationId());
            if (focus == null) {
                focus = NightHuntPreparationAffinityContent.focusForPreyFamily(mastery.lastRewardPreparationAffinityFocusId());
            }
            if (focus != null) {
                ArrayList<String> parts = new ArrayList<>();
                parts.add("Preparation bonus: " + focus.preparationDisplayName() + " · " + focus.laneLabel());
                String appliedBonus = matchingBonusText(
                        mastery.lastRewardPreparationMasteryBonus(),
                        mastery.lastRewardPreparationBloodBonus(),
                        focus.preyFamilyDisplayName());
                if (!appliedBonus.isBlank()) {
                    parts.add(appliedBonus);
                }
                String resonanceBonus = resonanceBonusText(
                        mastery.lastRewardResonanceMasteryBonus(),
                        mastery.lastRewardResonanceBloodBonus(),
                        focus.preyFamilyDisplayName());
                if (!resonanceBonus.isBlank()) {
                    parts.add(resonanceBonus);
                }
                String signatureReward = signatureRewardText(mastery, focus);
                if (!signatureReward.isBlank()) {
                    parts.add(signatureReward);
                }
                String pressureResonanceBonus = pressureResonanceBonusText(
                        mastery.lastRewardPressureResonanceMasteryBonus(),
                        mastery.lastRewardPressureResonanceAgeProgress(),
                        mastery.lastRewardPressureResonanceTargetAgeTierId());
                if (!pressureResonanceBonus.isBlank()) {
                    parts.add(pressureResonanceBonus);
                }
                parts.add(focus.bonusText());
                return String.join(" · ", parts);
            }
        }
        String preyFamilyId = mastery.lastRewardedPreyFamilyId() != null
                ? mastery.lastRewardedPreyFamilyId()
                : mastery.lastRewardAffinityId();
        if (preyFamilyId == null || preyFamilyId.isBlank()) {
            return "";
        }
        NightHuntPreparationAffinityContent.PreparationAffinity focus =
                NightHuntPreparationAffinityContent.focusForPreyFamily(preyFamilyId);
        if (focus == null) {
            return NightHuntPresentationText.humanize(preyFamilyId)
                    + " prey currently have no dedicated preparation lane.";
        }
        NightHuntPreparedLoadout loadout = NightHuntProgressionService.preparedLoadout(focus.preparationId(), null);
        ArrayList<String> parts = new ArrayList<>();
        parts.add("Matching lane: " + focus.preparationDisplayName() + " · " + focus.laneLabel());
        String matchBonus = matchingBonusText(loadout.affinityFocusMasteryBonus(), loadout.affinityFocusBloodBonus(), focus.preyFamilyDisplayName());
        if (!matchBonus.isBlank()) {
            parts.add(matchBonus);
        }
        parts.add(focus.bonusText());
        return String.join(" · ", parts);
    }

    @Nonnull
    private static String matchingBonusText(int masteryBonus, int bloodBonus, @Nonnull String preyFamilyDisplayName) {
        ArrayList<String> parts = new ArrayList<>();
        if (masteryBonus > 0) {
            parts.add("+" + masteryBonus + " mastery");
        }
        if (bloodBonus > 0) {
            parts.add("+" + bloodBonus + " blood");
        }
        if (parts.isEmpty()) {
            return "";
        }
        return String.join(" · ", parts) + " on matching " + preyFamilyDisplayName + " hunts";
    }

    @Nonnull
    private static String resonanceBonusText(int masteryBonus, int bloodBonus, @Nonnull String preyFamilyDisplayName) {
        ArrayList<String> parts = new ArrayList<>();
        if (masteryBonus > 0) {
            parts.add("+" + masteryBonus + " mastery");
        }
        if (bloodBonus > 0) {
            parts.add("+" + bloodBonus + " blood");
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "Resonance " + String.join(" · ", parts) + " on owned " + preyFamilyDisplayName + " hunts";
    }

    @Nonnull
    static String pressureResonanceBonusText(int masteryBonus,
                                             long ageProgressBonus,
                                             @Nullable String targetAgeTierId) {
        ArrayList<String> parts = new ArrayList<>();
        if (ageProgressBonus > 0) {
            parts.add("+" + ageProgressBonus + " age toward "
                    + NightHuntPresentationText.humanize(targetAgeTierId != null ? targetAgeTierId : "ancient"));
        }
        if (masteryBonus > 0) {
            parts.add("+" + masteryBonus + " mastery");
        }
        if (parts.isEmpty()) {
            return "";
        }
        return "Pressure resonance " + String.join(" · ", parts)
                + " on elder lineage hunts that end in crackdown pressure";
    }

    @Nonnull
    private static String signaturePreviewText(@Nonnull NightHuntPreparedLoadout loadout,
                                               @Nonnull NightHuntPreparationAffinityContent.PreparationAffinity focus) {
        NightHuntProgressionRegistry.AffinitySignatureDefinition signature =
                NightHuntProgressionRegistry.get().snapshot().affinitySignature(loadout.preparationId());
        if (signature == null) {
            return "";
        }
        String bundleText = signatureBundleText(
                signature.rewardBundle().masteryBonus(),
                signature.rewardBundle().bloodBonus(),
                signature.rewardBundle().ageProgressBonus(),
                signature.rewardBundle().affinityAmount(),
                focus.preyFamilyDisplayName());
        if (bundleText.isBlank()) {
            return "";
        }
        return "Signature pull: " + signature.displayName()
                + " · " + signaturePairingText(signature)
                + " · " + bundleText
                + " when resonance lands the full pairing";
    }

    @Nonnull
    private static String signatureRewardText(@Nonnull NightHuntMasterySnapshot mastery,
                                              @Nonnull NightHuntPreparationAffinityContent.PreparationAffinity focus) {
        if (mastery.lastRewardSignaturePackageId() == null || mastery.lastRewardSignaturePackageId().isBlank()) {
            return "";
        }
        NightHuntProgressionRegistry.AffinitySignatureDefinition signature =
                signatureDefinition(mastery.lastRewardPreparationId(), mastery.lastRewardSignaturePackageId());
        if (signature == null) {
            return "";
        }
        String bundleText = signatureBundleText(
                mastery.lastRewardSignatureMasteryBonus(),
                mastery.lastRewardSignatureBloodBonus(),
                mastery.lastRewardSignatureAgeProgress(),
                mastery.lastRewardSignatureAffinityAmount(),
                focus.preyFamilyDisplayName());
        if (bundleText.isBlank()) {
            return "";
        }
        return "Signature hunt: " + signature.displayName()
                + " · " + signaturePairingText(signature)
                + " · " + bundleText;
    }

    @Nullable
    private static NightHuntProgressionRegistry.AffinitySignatureDefinition signatureDefinition(@Nullable String preparationId,
                                                                                                @Nullable String packageId) {
        String normalizedPackageId = packageId != null ? packageId.trim().toLowerCase(Locale.ROOT) : null;
        if (normalizedPackageId == null || normalizedPackageId.isBlank()) {
            return null;
        }
        NightHuntProgressionRegistry.Snapshot snapshot = NightHuntProgressionRegistry.get().snapshot();
        if (preparationId != null) {
            NightHuntProgressionRegistry.AffinitySignatureDefinition signature = snapshot.affinitySignature(preparationId);
            if (signature != null && normalizedPackageId.equals(signature.id())) {
                return signature;
            }
        }
        for (NightHuntProgressionRegistry.PreparationDefinition preparation : snapshot.preparations()) {
            NightHuntProgressionRegistry.AffinitySignatureDefinition signature = preparation.affinitySignature();
            if (signature != null && normalizedPackageId.equals(signature.id())) {
                return signature;
            }
        }
        return null;
    }

    @Nonnull
    private static String signaturePairingText(@Nonnull NightHuntProgressionRegistry.AffinitySignatureDefinition signature) {
        NightHuntSpawnRegistry.EnvironmentOption environment = null;
        NightHuntSpawnRegistry.EncounterBeatOption encounterBeat = null;
        try {
            NightHuntSpawnRegistry registry = NightHuntSpawnRegistry.get();
            environment = registry.environment(signature.environmentId());
            encounterBeat = registry.encounterBeat(signature.encounterBeatId());
        } catch (IllegalStateException ignored) {
        }
        String environmentName = environment != null
                ? environment.displayName()
                : NightHuntPresentationText.humanize(signature.environmentId());
        String encounterBeatName = encounterBeat != null
                ? encounterBeat.displayName()
                : NightHuntPresentationText.humanize(signature.encounterBeatId());
        return environmentName + " → " + encounterBeatName;
    }

    @Nonnull
    private static String signatureBundleText(int masteryBonus,
                                              int bloodBonus,
                                              long ageProgressBonus,
                                              int affinityAmount,
                                              @Nonnull String preyFamilyDisplayName) {
        ArrayList<String> parts = new ArrayList<>();
        if (masteryBonus > 0) {
            parts.add("+" + masteryBonus + " mastery");
        }
        if (bloodBonus > 0) {
            parts.add("+" + bloodBonus + " blood");
        }
        if (ageProgressBonus > 0) {
            parts.add("+" + ageProgressBonus + " age progress");
        }
        if (affinityAmount > 0) {
            parts.add("Affinity " + preyFamilyDisplayName + " +" + affinityAmount);
        }
        return String.join(" · ", parts);
    }

    @Nonnull
    String quarryText() {
        return quarryRows().stream()
                .map(row -> row.name() + " · " + row.tags() + " · " + row.status())
                .toList()
                .stream()
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Nonnull
    List<QuarryRow> quarryRows() {
        ArrayList<QuarryRow> rows = new ArrayList<>();
        for (NightHuntSpawnRegistry.SpawnOption option : preyCatalogue) {
            boolean discovered = mastery.discoveredPreyRoleIds().contains(option.roleId());
            String name = discovered ? option.displayName() : "Unknown prey";
            String tags = NightHuntPresentationText.humanize(option.preyFamily())
                    + " / " + NightHuntPresentationText.archetypeName(option.archetype());
            String status = "tier " + option.visualTier()
                    + (option.elite() ? " · elite" : "")
                    + (discovered ? " · logged" : " · undiscovered");
            rows.add(new QuarryRow(
                    name,
                    tags,
                    status,
                    discovered ? (option.elite() ? "#facc15" : "#ef4444") : "#64748b",
                    discovered ? VampirismVisuals.ICON_PREY : VampirismVisuals.ICON_UNKNOWN,
                    "T" + option.visualTier(),
                    option.elite() ? "Elite" : discovered ? "Logged" : "Hidden"));
        }
        return List.copyOf(rows);
    }

    @Nonnull
    String footerText() {
        return switch (selectedTab) {
            case PREPARATIONS -> "Preparation changes save to your Vampirism player profile for the next hunt.";
            case RECORDS -> "Contracts track exact prey and resolution, while archetypes summarize long-term mastery.";
            case QUARRY -> "Discovered prey stay at the top of the log so repeat targets are easy to review.";
            default -> "Use the tabs to switch between summary, loadouts, records, and quarry notes.";
        };
    }

    @Nonnull
    Tab selectedTab() {
        return selectedTab;
    }

    @Nonnull
    String previewPreparationId() {
        return previewLoadout.preparationId();
    }

    @Nonnull
    private String threatName() {
        return switch (Math.max(0, Math.min(3, progress.worldThreatLevel))) {
            case 1 -> "hunter-watch";
            case 2 -> "hunter-crackdown";
            case 3 -> "crimson-dragnet";
            default -> "quiet";
        };
    }

    @Nonnull
    private static String preparationIcon(@Nullable String preparationId) {
        if (preparationId == null || preparationId.isBlank()) {
            return VampirismVisuals.ICON_ROUTE;
        }
        String normalized = preparationId.toLowerCase(Locale.ROOT);
        if (normalized.contains("siphon") || normalized.contains("blood")) {
            return VampirismVisuals.ICON_HEAT;
        }
        if (normalized.contains("dread") || normalized.contains("fear")) {
            return VampirismVisuals.ICON_THREAT;
        }
        if (normalized.contains("pursuit") || normalized.contains("trail")) {
            return VampirismVisuals.ICON_HUNT;
        }
        return VampirismVisuals.ICON_ROUTE;
    }

    @Nonnull
    private static NightHuntPreparedLoadout resolvePreviewLoadout(@Nonnull List<NightHuntPreparedLoadout> availableLoadouts,
                                                                  @Nonnull NightHuntPreparedLoadout currentLoadout,
                                                                  @Nullable String previewPreparationId) {
        String desiredId = previewPreparationId == null ? currentLoadout.preparationId() : previewPreparationId.trim();
        for (NightHuntPreparedLoadout loadout : availableLoadouts) {
            if (loadout.preparationId().equalsIgnoreCase(desiredId)) {
                return loadout;
            }
        }
        return currentLoadout;
    }

    @Nonnull
    private static String signedDelta(int value) {
        if (value > 0) {
            return "+" + value;
        }
        if (value < 0) {
            return Integer.toString(value);
        }
        return "0";
    }

    @Nonnull
    private static String trimMultiplier(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    @Nonnull
    private static String compactDetail(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 72) {
            return normalized;
        }
        int sentenceEnd = normalized.indexOf(". ");
        if (sentenceEnd >= 24 && sentenceEnd <= 72) {
            return normalized.substring(0, sentenceEnd + 1);
        }
        return normalized.substring(0, 69).trim() + "...";
    }

    @Nonnull
    private static String deltaAccent(int value) {
        if (value > 0) {
            return "#22c55e";
        }
        if (value < 0) {
            return "#f97316";
        }
        return "#9bb0c2";
    }

    @Nonnull
    private static String joinDashboardRows(@Nonnull List<DashboardRow> rows) {
        return rows.stream()
                .map(row -> row.label() + ": " + row.value() + (row.detail().isBlank() ? "" : " · " + row.detail()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Nonnull
    private static String joinMetrics(@Nonnull List<DashboardMetric> metrics) {
        return metrics.stream()
                .map(row -> row.label() + ": " + row.value() + (row.detail().isBlank() ? "" : " · " + row.detail()))
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Nonnull
    private static String joinChips(@Nonnull List<RewardChip> chips) {
        return chips.stream()
                .map(chip -> chip.label() + ": " + chip.value())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    @Nonnull
    private static String trimRadius(double value) {
        if (Math.abs(value - Math.round(value)) < 0.01d) {
            return Integer.toString((int) Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @Nonnull
    private static String roman(int value) {
        return switch (Math.max(1, Math.min(5, value))) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "I";
        };
    }
}
