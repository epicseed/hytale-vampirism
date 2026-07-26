package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.ui.ProgressionCardView;
import com.epicseed.epiccore.skill.ui.ProgressionSectionView;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.progression.SkillNodeState;
import com.epicseed.epiccore.skill.progression.SkillUnlockResult;
import com.epicseed.epiccore.skill.ui.SkillTreeLayoutBounds;
import com.epicseed.epiccore.skill.ui.SkillTreeNodeStateView;
import com.epicseed.epiccore.skill.ui.SkillTreeUiAdapter;
import com.epicseed.epiccore.skill.ui.SkillTreeUnlockResultView;
import com.epicseed.epiccore.vampirism.domain.player.NamedHuntProgress;
import com.epicseed.epiccore.vampirism.domain.player.PersistedNightHuntState;
import com.epicseed.epiccore.vampirism.domain.player.RitualProgressState;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuityService;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionService;
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.progression.VampirismProgressionFeaturePolicy;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeExposureLevel;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatPolicy;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;
import com.epicseed.vampirism.domain.skill.SkillTreePresenter;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import org.joml.Vector2d;

public final class VampirismSkillTreeUiAdapter implements SkillTreeUiAdapter {

    private final ProgressionDefinitionProvider definitionProvider;
    private final SkillProgressionAccess progressionAccess;
    private final Supplier<Vector2d> highestPositionSupplier;
    private final SkillTreePresenter presenter;
    private final SkillTreeManager skillTreeManager;
    private final VampiricLineageService lineageService;
    private final VampiricRitualService ritualService;
    private final MasqueradeHeatService masqueradeHeatService;
    private final Supplier<? extends VampirismProgressionFeaturePolicy> featurePolicySupplier;

    public VampirismSkillTreeUiAdapter(@Nonnull ProgressionDefinitionProvider definitionProvider,
                                       @Nonnull SkillProgressionAccess progressionAccess,
                                       @Nonnull Supplier<Vector2d> highestPositionSupplier,
                                       @Nonnull SkillTreePresenter presenter,
                                       @Nonnull SkillTreeManager skillTreeManager,
                                       @Nonnull VampiricLineageService lineageService,
                                       @Nonnull VampiricRitualService ritualService,
                                       @Nonnull MasqueradeHeatService masqueradeHeatService) {
        this(definitionProvider,
                progressionAccess,
                highestPositionSupplier,
                presenter,
                skillTreeManager,
                lineageService,
                ritualService,
                masqueradeHeatService,
                VampirismProgressionFeaturePolicy::allEnabled);
    }

    public VampirismSkillTreeUiAdapter(@Nonnull ProgressionDefinitionProvider definitionProvider,
                                       @Nonnull SkillProgressionAccess progressionAccess,
                                       @Nonnull Supplier<Vector2d> highestPositionSupplier,
                                       @Nonnull SkillTreePresenter presenter,
                                       @Nonnull SkillTreeManager skillTreeManager,
                                       @Nonnull VampiricLineageService lineageService,
                                       @Nonnull VampiricRitualService ritualService,
                                       @Nonnull MasqueradeHeatService masqueradeHeatService,
                                       @Nonnull Supplier<? extends VampirismProgressionFeaturePolicy> featurePolicySupplier) {
        this.definitionProvider = definitionProvider;
        this.progressionAccess = progressionAccess;
        this.highestPositionSupplier = highestPositionSupplier;
        this.presenter = presenter;
        this.skillTreeManager = skillTreeManager;
        this.lineageService = lineageService;
        this.ritualService = ritualService;
        this.masqueradeHeatService = masqueradeHeatService;
        this.featurePolicySupplier = featurePolicySupplier;
    }

    @Override
    @Nonnull
    public List<Skill> allSkills() {
        return new ArrayList<>(definitionProvider.getAllSkills());
    }

    @Override
    @Nullable
    public Skill skill(@Nonnull String skillId) {
        return definitionProvider.getSkill(skillId);
    }

    @Override
    @Nonnull
    public SkillTreeLayoutBounds layoutBounds() {
        Vector2d highestPosition = highestPositionSupplier.get();
        return new SkillTreeLayoutBounds((int) highestPosition.x(), (int) highestPosition.y());
    }

    @Override
    public int availablePoints(@Nonnull UUID uuid) {
        return progressionAccess.getSkillPoints(uuid);
    }

    @Override
    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return progressionAccess.hasSkill(uuid, skillId);
    }

    @Override
    @Nonnull
    public SkillTreeNodeStateView stateFor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        SkillNodeState state = presenter.stateFor(skill, uuid);
        return new SkillTreeNodeStateView(
                state.wip(),
                state.unlocked(),
                state.canUnlock(),
                state.depsMet(),
                state.availablePoints(),
                state.costText(),
                state.unlockStatus(),
                state.indicatorColor());
    }

    @Override
    @Nonnull
    public String buildDescription(@Nonnull Skill skill) {
        return presenter.buildDescription(skill);
    }

    @Override
    @Nonnull
    public List<ProgressionSectionView> progressionSections(@Nonnull UUID uuid) {
        if (!VampirePlayerStateStore.isInitialized()) {
            return List.of();
        }

        VampirePlayerStateStore store = VampirePlayerStateStore.get();
        Map<String, Integer> bloodAffinities = store.getBloodAffinities(uuid);
        Map<String, RitualProgressState> ritualStates = store.getAllRitualProgress(uuid);
        Map<String, NamedHuntProgress> namedHuntProgress = store.getAllNamedHuntProgress(uuid);
        List<VampiricLineageEvaluation> lineageEvaluations = lineageService.evaluateAll(uuid);
        List<VampiricRitualEvaluation> ritualEvaluations = ritualEvaluations(uuid, store);
        String ageTierId = store.getAgeTierId(uuid);
        VampirismProgressionFeaturePolicy featurePolicy = featurePolicySupplier.get();

        int trackedRituals = Math.max(ritualStates.size(), ritualEvaluations.size());
        long activeRituals = ritualEvaluations.stream()
                .filter(VampiricRitualEvaluation::active)
                .count();
        long availableRituals = ritualEvaluations.stream()
                .filter(VampiricRitualEvaluation::available)
                .count();
        long completedRituals = ritualEvaluations.stream()
                .filter(VampiricRitualEvaluation::completed)
                .count();
        long lockedRituals = trackedRituals - activeRituals - availableRituals - completedRituals;

        long activeHunts = namedHuntProgress.values().stream()
                .filter(progress -> progress.activeContractId != null && !progress.activeContractId.isBlank())
                .count();
        int completedHunts = namedHuntProgress.values().stream()
                .mapToInt(progress -> progress.completionCount)
                .sum();
        NamedHuntProgress nightHuntProgress = namedHuntProgress.getOrDefault("night-hunt", new NamedHuntProgress());
        var huntMastery = NightHuntProgressionService.snapshot(nightHuntProgress);
        var huntLoadout = NightHuntProgressionService.preparedLoadout(nightHuntProgress);
        NightHuntContinuitySnapshot continuity = NightHuntContinuityService.snapshot(nightHuntProgress);
        PersistedNightHuntState persistedNightHuntState = store.getPersistedNightHuntState(uuid);
        MasqueradeHeatSnapshot masquerade = masqueradeHeatService.snapshot(uuid, System.currentTimeMillis());
        long availableLineages = lineageEvaluations.stream().filter(VampiricLineageEvaluation::available).count();
        VampiricLineageEvaluation selectedLineage = lineageEvaluations.stream()
                .filter(VampiricLineageEvaluation::selected)
                .findFirst()
                .orElse(null);

        ArrayList<ProgressionSectionView> sections = new ArrayList<>();
        if (featurePolicy.ageTierProgressionEnabled() || featurePolicy.bloodAffinityProgressionEnabled()) {
            sections.add(VampiricAgeTierSectionFactory.build(
                    uuid,
                    bloodAffinities,
                    featurePolicy.ageTierProgressionEnabled(),
                    featurePolicy.bloodAffinityProgressionEnabled()));
        }
        sections.add(new ProgressionSectionView(
                        "lineage",
                        "Lineage",
                        "Lineage Legacy",
                        "No lineage details are available.",
                        buildLineageCards(
                                store.getLineageUnlockedAtMs(uuid),
                                store.getLineageRespecCount(uuid),
                                bloodAffinities,
                                lineageEvaluations,
                                selectedLineage,
                                availableLineages)));
        sections.add(new ProgressionSectionView(
                        "rituals",
                        "Rituals",
                        "Ritual Tracking",
                        "No ritual progress has been recorded yet.",
                        List.of(
                                new ProgressionCardView(
                                        "Tracked Rituals",
                                        Integer.toString(trackedRituals),
                                        trackedRituals == 0
                                                ? "No ritual ids have been persisted for this player."
                                                : activeRituals + " active · " + completedRituals + " completed",
                                        "#22c55e",
                                        VampirismVisuals.ICON_RITUAL,
                                        trackedRituals == 0 ? "Empty" : "Tracked",
                                        trackedRituals == 0 ? VampirismVisuals.NEUTRAL : VampirismVisuals.SAFE),
                                new ProgressionCardView(
                                        "Availability",
                                        availableRituals + " ready",
                                        lockedRituals + " locked · " + activeRituals + " active",
                                        "#14b8a6",
                                        VampirismVisuals.ICON_READY,
                                        availableRituals > 0 ? "Ready" : "Wait",
                                        availableRituals > 0 ? VampirismVisuals.SAFE : VampirismVisuals.WARNING),
                                new ProgressionCardView(
                                        "Named Hunts",
                                        namedHuntProgress.size() + " tracked",
                                        activeHunts + " active hunts · " + completedHunts + " completions",
                                        "#06b6d4",
                                        VampirismVisuals.ICON_HUNT,
                                        activeHunts > 0 ? "Active" : "Log",
                                        activeHunts > 0 ? VampirismVisuals.WARNING : VampirismVisuals.INFO))));
        sections.add(new ProgressionSectionView(
                        "hunt",
                        "Hunt",
                        featurePolicy.nightHuntProgressionEnabled() ? "Night Hunt Mastery" : "Night Hunt Records",
                        "No hunt records have been recorded yet.",
                        huntCards(huntMastery, huntLoadout, featurePolicy)));
        sections.add(new ProgressionSectionView(
                        "heat",
                        "Heat",
                        "Masquerade Heat",
                        "No masquerade telemetry has been recorded yet.",
                        buildHeatCards(
                                huntMastery,
                                masquerade,
                                masqueradeHeatService.policy(),
                                featurePolicy.ageTierProgressionEnabled() ? ageTierId : null,
                                selectedLineage,
                                featurePolicy.bloodAffinityProgressionEnabled() ? bloodAffinities : Map.of(),
                                lineageEvaluations,
                                continuity,
                                persistedNightHuntState,
                                nightHuntProgress,
                                featurePolicy)));
        return List.copyOf(sections);
    }

    @Nonnull
    private static List<ProgressionCardView> huntCards(@Nonnull NightHuntMasterySnapshot huntMastery,
                                                      @Nonnull com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout huntLoadout,
                                                      @Nonnull VampirismProgressionFeaturePolicy featurePolicy) {
        ArrayList<ProgressionCardView> cards = new ArrayList<>();
        if (featurePolicy.nightHuntProgressionEnabled()) {
            cards.add(new ProgressionCardView(
                    "Hunt Rank",
                    huntMastery.currentRank().displayName(),
                    huntMastery.masteryPoints() + " mastery · tier " + huntMastery.baseVisualTier(),
                    huntMastery.currentRank().accentColor(),
                    VampirismVisuals.ICON_HUNT,
                    "Rank",
                    huntMastery.currentRank().accentColor()));
        }
        cards.add(
                                new ProgressionCardView(
                                        "Compendium",
                                        huntMastery.discoveredPreyRoleIds().size() + " prey",
                                        huntMastery.uniqueContractsCompleted() + " contracts · " + huntMastery.eliteCompletionCount() + " elite claims",
                                        "#ef4444",
                                        VampirismVisuals.ICON_PREY,
                                        huntMastery.discoveredPreyRoleIds().isEmpty() ? "Unknown" : "Known",
                                        huntMastery.discoveredPreyRoleIds().isEmpty() ? VampirismVisuals.NEUTRAL : VampirismVisuals.DANGER));
        cards.add(new ProgressionCardView(
                                        "Archetype Mastery",
                                        huntMastery.archetypeCompletionCounts().size() + " tracked",
                                        summarizeTopArchetype(huntMastery),
                                        "#dc2626",
                                        VampirismVisuals.ICON_THREAT,
                                        "Patterns",
                                        VampirismVisuals.DANGER));
        if (featurePolicy.nightHuntProgressionEnabled()) {
            cards.add(new ProgressionCardView(
                    "Next Rank",
                    huntMastery.nextRank() != null ? huntMastery.nextRank().displayName() : "Max rank",
                    huntMastery.nextRank() != null
                            ? huntMastery.masteryToNextRank() + " mastery remaining"
                            : "All hunt mastery milestones claimed",
                    "#f59e0b",
                    VampirismVisuals.ICON_REWARD,
                    huntMastery.nextRank() != null ? "Goal" : "Max",
                    huntMastery.nextRank() != null ? VampirismVisuals.WARNING : VampirismVisuals.SPECIAL));
        }
        cards.add(new ProgressionCardView(
                                        "Preparation",
                                        huntLoadout.preparationDisplayName(),
                                        summarizePreparationDetail(huntLoadout, featurePolicy),
                                        "#f59e0b",
                                        VampirismVisuals.ICON_ROUTE,
                                        "Loadout",
                                        VampirismVisuals.WARNING));
        cards.add(new ProgressionCardView(
                                        "Recent Reward",
                                        huntMastery.lastRewardedPreyRoleId() != null
                                                ? NightHuntPresentationText.preyName(huntMastery.lastRewardedPreyRoleId())
                                                : "No recent hunt",
                                        summarizeRecentHuntReward(huntMastery),
                                        "#f59e0b",
                                        VampirismVisuals.ICON_REWARD,
                                        huntMastery.lastRewardedPreyRoleId() != null ? "Claimed" : "None",
                                        huntMastery.lastRewardedPreyRoleId() != null ? VampirismVisuals.SAFE : VampirismVisuals.NEUTRAL));
        return List.copyOf(cards);
    }

    @Nonnull
    private List<VampiricRitualEvaluation> ritualEvaluations(@Nonnull UUID uuid,
                                                             @Nonnull VampirePlayerStateStore store) {
        if (ritualService.registry().definitions().isEmpty()) {
            return List.of();
        }
        VampiricRitualContext context = new VampiricRitualContext(
                uuid,
                store.getPersistedBlood(uuid),
                store.getCompletedNightHunts(uuid),
                store.getAgeTierId(uuid),
                progressionAccess.getUnlockedSkillIds(uuid),
                store.getMilestoneProofIds(uuid),
                store.getBloodAffinities(uuid),
                inferredRitualTags(uuid, store));
        return ritualService.registry().definitions().values().stream()
                .map(definition -> ritualService.evaluate(uuid, definition.id(), context))
                .toList();
    }

    @Nonnull
    private static java.util.Set<String> inferredRitualTags(@Nonnull UUID uuid,
                                                            @Nonnull VampirePlayerStateStore store) {
        java.util.LinkedHashSet<String> tags = new java.util.LinkedHashSet<>();
        if (store.isInfected(uuid)) {
            tags.add("infected");
        }
        return java.util.Set.copyOf(tags);
    }

    @Override
    @Nonnull
    public SkillTreeUnlockResultView unlock(@Nonnull UUID uuid, @Nonnull Skill skill) {
        SkillUnlockResult result = skillTreeManager.unlockDetailed(uuid, skill);
        return new SkillTreeUnlockResultView(result.unlocked(), result.message());
    }

    @Override
    public void resetPlayer(@Nonnull UUID uuid) {
        skillTreeManager.resetPlayer(uuid);
    }

    @Nonnull
    private static String formatIdentifier(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return NightHuntPresentationText.humanize(value);
    }

    @Nonnull
    private static String formatInstant(long epochMs, @Nonnull String fallback) {
        if (epochMs <= 0L) {
            return fallback;
        }
        return java.time.Instant.ofEpochMilli(epochMs).toString();
    }

    @Nonnull
    private static String formatHeat(double heat) {
        return String.format(java.util.Locale.ROOT, "%.1f", Math.max(0.0d, heat));
    }

    @Nonnull
    private static String summarizeTopArchetype(@Nonnull com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot mastery) {
        return mastery.archetypeCompletionCounts().entrySet().stream()
                .max(java.util.Map.Entry.<String, Integer>comparingByValue()
                        .thenComparing(java.util.Map.Entry::getKey))
                .map(entry -> NightHuntPresentationText.archetypeName(entry.getKey()) + " · " + entry.getValue() + " kills")
                .orElse("No archetype kills recorded yet.");
    }

    @Nonnull
    static String summarizeRecentHuntReward(@Nonnull com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot mastery) {
        if (mastery.lastRewardedAtMs() <= 0L) {
            return "No hunt reward has been recorded yet.";
        }
        ArrayList<String> parts = new ArrayList<>();
        if (mastery.lastRewardSkillPoints() > 0) parts.add("+" + mastery.lastRewardSkillPoints() + " skill");
        if (mastery.lastRewardMasteryPoints() > 0) parts.add("+" + mastery.lastRewardMasteryPoints() + " mastery");
        if (mastery.lastRewardBlood() > 0) parts.add("+" + mastery.lastRewardBlood() + " blood");
        if (mastery.lastRewardAgeProgress() > 0) parts.add("+" + mastery.lastRewardAgeProgress() + " age");
        if (mastery.lastRewardAffinityAmount() > 0 && mastery.lastRewardAffinityId() != null) {
            parts.add(formatIdentifier(mastery.lastRewardAffinityId(), "Affinity") + " +" + mastery.lastRewardAffinityAmount());
        }
        if (mastery.lastRewardedArchetypeMilestoneId() != null) {
            parts.add(formatIdentifier(mastery.lastRewardedArchetypeMilestoneId(), "Milestone"));
        }
        String summary = parts.isEmpty() ? "No tangible bonus recorded." : String.join(" · ", parts);
        String laneReadout = HuntCompendiumModel.recentRewardLaneText(mastery);
        return laneReadout.isBlank() ? summary : summary + " · " + laneReadout;
    }

    @Nonnull
    private static String pressureResonanceHeatRecap(@Nonnull NightHuntMasterySnapshot mastery) {
        String recap = HuntCompendiumModel.pressureResonanceBonusText(
                mastery.lastRewardPressureResonanceMasteryBonus(),
                mastery.lastRewardPressureResonanceAgeProgress(),
                mastery.lastRewardPressureResonanceTargetAgeTierId());
        return recap.isBlank()
                ? ""
                : "Last pressure payoff: " + recap + ".";
    }

    @Nonnull
    static String summarizePreparationDetail(@Nonnull com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout loadout) {
        return summarizePreparationDetail(loadout, VampirismProgressionFeaturePolicy.allEnabled());
    }

    @Nonnull
    static String summarizePreparationDetail(@Nonnull com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout loadout,
                                             @Nonnull VampirismProgressionFeaturePolicy featurePolicy) {
        if (!featurePolicy.bloodAffinityProgressionEnabled()) {
            return loadout.modeDisplayName() + " · Open the Hunt Briefing to preview and change.";
        }
        return loadout.modeDisplayName() + " · " + HuntCompendiumModel.preparationRecapText(loadout)
                + " Open the Hunt Briefing to preview and change.";
    }

    @Nonnull
    private static String formatExposureLevel(@Nonnull MasqueradeExposureLevel level) {
        return switch (level) {
            case QUIET -> "Quiet";
            case WATCHED -> "Watched";
            case HUNTED -> "Hunted";
            case BREACHED -> "Breached";
        };
    }

    @Nonnull
    static List<ProgressionCardView> buildHeatCards(@Nonnull NightHuntMasterySnapshot huntMastery,
                                                    @Nonnull MasqueradeHeatSnapshot masquerade,
                                                    @Nonnull MasqueradeHeatPolicy policy,
                                                    @Nullable String ageTierId,
                                                    @Nullable VampiricLineageEvaluation selectedLineage,
                                                    @Nonnull Map<String, Integer> bloodAffinities,
                                                    @Nonnull List<VampiricLineageEvaluation> lineageEvaluations,
                                                    @Nonnull NightHuntContinuitySnapshot continuity,
                                                    @Nonnull PersistedNightHuntState persistedNightHuntState,
                                                    @Nonnull NamedHuntProgress nightHuntProgress) {
        return buildHeatCards(
                huntMastery,
                masquerade,
                policy,
                ageTierId,
                selectedLineage,
                bloodAffinities,
                lineageEvaluations,
                continuity,
                persistedNightHuntState,
                nightHuntProgress,
                VampirismProgressionFeaturePolicy.allEnabled());
    }

    @Nonnull
    static List<ProgressionCardView> buildHeatCards(@Nonnull NightHuntMasterySnapshot huntMastery,
                                                    @Nonnull MasqueradeHeatSnapshot masquerade,
                                                    @Nonnull MasqueradeHeatPolicy policy,
                                                    @Nullable String ageTierId,
                                                    @Nullable VampiricLineageEvaluation selectedLineage,
                                                    @Nonnull Map<String, Integer> bloodAffinities,
                                                    @Nonnull List<VampiricLineageEvaluation> lineageEvaluations,
                                                    @Nonnull NightHuntContinuitySnapshot continuity,
                                                    @Nonnull PersistedNightHuntState persistedNightHuntState,
                                                    @Nonnull NamedHuntProgress nightHuntProgress,
                                                    @Nonnull VampirismProgressionFeaturePolicy featurePolicy) {
        MasqueradeHeatThresholdText.ThresholdView nextThreshold =
                MasqueradeHeatThresholdText.nextThreshold(masquerade, policy);
        LineageWindowOpportunity.View opportunity = LineageWindowOpportunity.resolve(
                masquerade,
                bloodAffinities,
                lineageEvaluations);
        HuntCrackdownText.View crackdown = HuntCrackdownText.resolve(persistedNightHuntState, continuity, nightHuntProgress);
        PressureOutlookText.View pressureOutlook = PressureOutlookText.resolve(continuity, selectedLineage);
        PressureDriversText.View pressureDrivers = PressureDriversText.resolve(continuity, selectedLineage);
        IdentityPressureText.View identityPressure = IdentityPressureText.resolve(ageTierId, selectedLineage);
        String pressureResonanceRecap = featurePolicy.ageTierProgressionEnabled()
                || featurePolicy.nightHuntProgressionEnabled()
                ? pressureResonanceHeatRecap(huntMastery)
                : "";
        ArrayList<ProgressionCardView> cards = new ArrayList<>();
        cards.add(new ProgressionCardView(
                        "Current Exposure",
                        formatExposureLevel(masquerade.exposureLevel()) + " · " + formatHeat(masquerade.heat()) + " heat",
                        heatStateDetail(masquerade),
                        heatAccent(masquerade.exposureLevel()),
                        VampirismVisuals.ICON_HEAT,
                        formatExposureLevel(masquerade.exposureLevel()),
                        heatAccent(masquerade.exposureLevel())));
        cards.add(new ProgressionCardView(
                        "Next Threshold",
                        nextThreshold.value(),
                        compactDetail(nextThreshold.detail()),
                        nextThreshold.accentColor(),
                        VampirismVisuals.ICON_THREAT,
                        "Next",
                        nextThreshold.accentColor()));
        cards.add(new ProgressionCardView(
                        "Current Risk",
                        currentRiskValue(masquerade),
                        currentRiskDetail(masquerade),
                        masquerade.progressionLocked() ? "#ef4444" : heatAccent(masquerade.exposureLevel()),
                        VampirismVisuals.ICON_THREAT,
                        masquerade.progressionLocked() ? "Locked" : "Route",
                        masquerade.progressionLocked() ? VampirismVisuals.DANGER : heatAccent(masquerade.exposureLevel())));
        cards.add(new ProgressionCardView(
                        "Next Hunt Window",
                        crackdown.value(),
                        compactDetail(crackdown.detail()),
                        crackdown.accentColor(),
                        VampirismVisuals.ICON_HUNT,
                        "Window",
                        crackdown.accentColor()));
        cards.add(new ProgressionCardView(
                        "Identity Pressure",
                        identityPressure.value(),
                        compactDetail(identityPressure.detail()),
                        identityPressure.accentColor(),
                        VampirismVisuals.ICON_LINEAGE,
                        "Identity",
                        identityPressure.accentColor()));
        cards.add(new ProgressionCardView(
                        "Pressure Outlook",
                        pressureOutlook.value(),
                        pressureResonanceRecap.isBlank()
                                ? compactDetail(pressureOutlook.detail())
                                : joinCompact(pressureOutlook.detail(), pressureResonanceRecap),
                        pressureOutlook.accentColor(),
                        VampirismVisuals.ICON_ROUTE,
                        "Outlook",
                        pressureOutlook.accentColor()));
        cards.add(new ProgressionCardView(
                        "Pressure Drivers",
                        pressureDrivers.value(),
                        compactDetail(pressureDrivers.detail()),
                        pressureDrivers.accentColor(),
                        VampirismVisuals.ICON_RECORD,
                        "Drivers",
                        pressureDrivers.accentColor()));
        if (featurePolicy.bloodAffinityProgressionEnabled()) {
            cards.add(new ProgressionCardView(
                        "Current Opportunity",
                        opportunity.value(),
                        compactDetail(opportunity.detail()),
                        opportunity.accentColor(),
                        VampirismVisuals.ICON_REWARD,
                        "Chance",
                        opportunity.accentColor()));
        }
        return List.copyOf(cards);
    }

    @Nonnull
    static List<ProgressionCardView> buildLineageCards(long lineageUnlockedAtMs,
                                                               int lineageRespecCount,
                                                               @Nonnull Map<String, Integer> bloodAffinities,
                                                               @Nonnull List<VampiricLineageEvaluation> lineageEvaluations,
                                                               @Nullable VampiricLineageEvaluation selectedLineage,
                                                               long availableLineages) {
        ArrayList<ProgressionCardView> cards = new ArrayList<>();
        cards.add(new ProgressionCardView(
                "Current Lineage",
                selectedLineage != null
                        ? selectedLineage.definition().displayName()
                        : "Unbound",
                (selectedLineage != null ? selectedLineage.clan().displayName() : "No clan selected")
                        + " · Unlocked at: " + formatInstant(lineageUnlockedAtMs, "Not yet unlocked"),
                selectedLineage != null ? selectedLineage.clan().accentColor() : "#ef4444",
                VampirismVisuals.ICON_LINEAGE,
                selectedLineage != null ? "Bound" : "Open",
                selectedLineage != null ? selectedLineage.clan().accentColor() : VampirismVisuals.DANGER));
        cards.add(new ProgressionCardView(
                "Lineage Milestone",
                selectedLineage != null
                        ? selectedLineage.definition().perks().size() + " perk"
                                + (selectedLineage.definition().perks().size() == 1 ? "" : "s")
                        : availableLineages > 0
                        ? availableLineages + " ready"
                        : "Await rites",
                selectedLineage != null
                        ? lineagePerkSummary(selectedLineage.definition())
                        : nextLineageLead(lineageEvaluations, bloodAffinities, availableLineages),
                selectedLineage != null
                        ? selectedLineage.clan().accentColor()
                        : availableLineages > 0 ? "#22c55e" : "#f97316",
                VampirismVisuals.ICON_REWARD,
                selectedLineage != null ? "Perks" : availableLineages > 0 ? "Ready" : "Locked",
                selectedLineage != null
                        ? selectedLineage.clan().accentColor()
                        : availableLineages > 0 ? VampirismVisuals.SAFE : VampirismVisuals.WARNING));
        cards.add(new ProgressionCardView(
                "Respec Count",
                Integer.toString(lineageRespecCount),
                "Eligible lineages: " + availableLineages + " / " + lineageEvaluations.size(),
                "#f97316",
                VampirismVisuals.ICON_ROUTE,
                "Respec",
                lineageRespecCount > 0 ? VampirismVisuals.WARNING : VampirismVisuals.NEUTRAL));
        for (VampiricLineageEvaluation evaluation : lineageEvaluations) {
            String status = evaluation.selected() ? "Selected" : evaluation.available() ? "Available" : "Locked";
            String detail = lineagePerkSummary(evaluation.definition());
            if (!evaluation.available()) {
                String blockerSummary = LineageRequirementText.blockerSummary(evaluation, bloodAffinities);
                if (!blockerSummary.isBlank()) {
                    detail = detail + " · " + blockerSummary;
                }
            }
            cards.add(new ProgressionCardView(
                    evaluation.definition().displayName(),
                    status,
                    detail,
                    evaluation.clan().accentColor(),
                    VampirismVisuals.ICON_LINEAGE,
                    status,
                    evaluation.selected()
                            ? evaluation.clan().accentColor()
                            : evaluation.available() ? VampirismVisuals.SAFE : VampirismVisuals.NEUTRAL));
        }
        return List.copyOf(cards);
    }

    @Nonnull
    private static String lineagePerkSummary(@Nonnull VampiricLineageDefinition definition) {
        if (definition.perks().isEmpty()) {
            return definition.description();
        }
        return definition.perks().stream()
                .map(perk -> perk.displayName() + ": " + perk.description())
                .reduce((left, right) -> left + " · " + right)
                .orElse(definition.description());
    }

    @Nonnull
    private static String nextLineageLead(@Nonnull List<VampiricLineageEvaluation> lineageEvaluations,
                                          @Nonnull Map<String, Integer> bloodAffinities,
                                          long availableLineages) {
        if (availableLineages > 0) {
            return lineageEvaluations.stream()
                    .filter(VampiricLineageEvaluation::available)
                    .findFirst()
                    .map(evaluation -> evaluation.definition().displayName() + " is ready to claim.")
                    .orElse("A lineage is ready to claim.");
        }
        return lineageEvaluations.stream()
                .sorted(java.util.Comparator
                        .comparingInt((VampiricLineageEvaluation evaluation) -> evaluation.blockingReasons().size())
                        .thenComparing(evaluation -> evaluation.definition().displayName()))
                .findFirst()
                .map(evaluation -> {
                    if (evaluation.blockingReasons().isEmpty()) {
                        return "Complete more rites and hunts to reveal your first lineage.";
                    }
                    String action = LineageRequirementText.primaryActionText(evaluation, bloodAffinities);
                    if (action == null) {
                        return "Complete more rites and hunts to reveal your first lineage.";
                    }
                    return evaluation.definition().displayName() + " needs you to " + action + ".";
                })
                .orElse("Complete more rites and hunts to reveal your first lineage.");
    }

    @Nonnull
    private static String currentRiskValue(@Nonnull MasqueradeHeatSnapshot masquerade) {
        if (masquerade.progressionLocked()) {
            return "Progression locked";
        }
        if (masquerade.hunterPressure() > 0) {
            return "Pressure " + masquerade.hunterPressure();
        }
        return "Routes clear";
    }

    @Nonnull
    private static String currentRiskDetail(@Nonnull MasqueradeHeatSnapshot masquerade) {
        String strikeDetail = masquerade.strikeCount() > 0
                ? " · " + masquerade.strikeCount() + " strike" + (masquerade.strikeCount() == 1 ? "" : "s")
                : "";
        if (masquerade.progressionLocked()) {
            return "Shed exposure to reopen low-heat unlocks" + strikeDetail;
        }
        return switch (masquerade.exposureLevel()) {
            case QUIET -> strikeDetail.isBlank()
                    ? "No active route pressure"
                    : "Heat low, hunters still alert" + strikeDetail;
            case WATCHED -> "Routes under watch" + strikeDetail;
            case HUNTED -> "Pursuit risk high" + strikeDetail;
            case BREACHED -> "Exposure breached" + strikeDetail;
        };
    }

    @Nonnull
    private static String heatStateDetail(@Nonnull MasqueradeHeatSnapshot masquerade) {
        ArrayList<String> parts = new ArrayList<>();
        if (masquerade.hunterPressure() > 0) {
            parts.add("Pressure " + masquerade.hunterPressure());
        }
        if (masquerade.strikeCount() > 0) {
            parts.add(masquerade.strikeCount() + " strike" + (masquerade.strikeCount() == 1 ? "" : "s"));
        }
        if (masquerade.progressionLocked()) {
            parts.add("locked");
        }
        return parts.isEmpty() ? "Routes quiet" : String.join(" · ", parts);
    }

    @Nonnull
    private static String joinCompact(@Nonnull String primary, @Nonnull String secondary) {
        String compactPrimary = compactDetail(primary);
        String compactSecondary = compactDetail(secondary);
        if (compactPrimary.isBlank()) {
            return compactSecondary;
        }
        if (compactSecondary.isBlank()) {
            return compactPrimary;
        }
        return compactPrimary + " · " + compactSecondary;
    }

    @Nonnull
    private static String compactDetail(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 78) {
            return normalized;
        }
        int sentenceEnd = normalized.indexOf(". ");
        if (sentenceEnd >= 24 && sentenceEnd <= 78) {
            return normalized.substring(0, sentenceEnd + 1);
        }
        return normalized.substring(0, 75).trim() + "...";
    }

    @Nonnull
    private static String heatAccent(@Nonnull MasqueradeExposureLevel level) {
        return switch (level) {
            case QUIET -> "#22c55e";
            case WATCHED -> "#f59e0b";
            case HUNTED -> "#f97316";
            case BREACHED -> "#ef4444";
        };
    }

}
