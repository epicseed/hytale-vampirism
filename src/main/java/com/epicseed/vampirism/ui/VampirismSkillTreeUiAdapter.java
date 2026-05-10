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
import com.epicseed.epiccore.vampirism.domain.player.RitualProgressState;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuityService;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionService;
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeExposureLevel;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatPolicy;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;
import com.epicseed.vampirism.domain.skill.SkillTreePresenter;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.hypixel.hytale.math.vector.Vector2d;

public final class VampirismSkillTreeUiAdapter implements SkillTreeUiAdapter {

    private final ProgressionDefinitionProvider definitionProvider;
    private final SkillProgressionAccess progressionAccess;
    private final Supplier<Vector2d> highestPositionSupplier;
    private final SkillTreePresenter presenter;
    private final SkillTreeManager skillTreeManager;
    private final VampiricLineageService lineageService;
    private final VampiricRitualService ritualService;
    private final MasqueradeHeatService masqueradeHeatService;

    public VampirismSkillTreeUiAdapter(@Nonnull ProgressionDefinitionProvider definitionProvider,
                                       @Nonnull SkillProgressionAccess progressionAccess,
                                       @Nonnull Supplier<Vector2d> highestPositionSupplier,
                                       @Nonnull SkillTreePresenter presenter,
                                       @Nonnull SkillTreeManager skillTreeManager,
                                       @Nonnull VampiricLineageService lineageService,
                                       @Nonnull VampiricRitualService ritualService,
                                       @Nonnull MasqueradeHeatService masqueradeHeatService) {
        this.definitionProvider = definitionProvider;
        this.progressionAccess = progressionAccess;
        this.highestPositionSupplier = highestPositionSupplier;
        this.presenter = presenter;
        this.skillTreeManager = skillTreeManager;
        this.lineageService = lineageService;
        this.ritualService = ritualService;
        this.masqueradeHeatService = masqueradeHeatService;
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
        return new SkillTreeLayoutBounds((int) highestPosition.getX(), (int) highestPosition.getY());
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
        MasqueradeHeatSnapshot masquerade = masqueradeHeatService.snapshot(uuid, System.currentTimeMillis());
        long availableLineages = lineageEvaluations.stream().filter(VampiricLineageEvaluation::available).count();
        VampiricLineageEvaluation selectedLineage = lineageEvaluations.stream()
                .filter(VampiricLineageEvaluation::selected)
                .findFirst()
                .orElse(null);

        return List.of(
                VampiricAgeTierSectionFactory.build(uuid, bloodAffinities),
                new ProgressionSectionView(
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
                                availableLineages)),
                new ProgressionSectionView(
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
                                        "#22c55e"),
                                new ProgressionCardView(
                                        "Availability",
                                        availableRituals + " ready",
                                        lockedRituals + " locked · " + activeRituals + " active",
                                        "#14b8a6"),
                                new ProgressionCardView(
                                        "Named Hunts",
                                        namedHuntProgress.size() + " tracked",
                                        activeHunts + " active hunts · " + completedHunts + " completions",
                                        "#06b6d4"))),
                new ProgressionSectionView(
                        "hunt",
                        "Hunt",
                        "Night Hunt Mastery",
                        "No hunt mastery has been recorded yet.",
                        List.of(
                                new ProgressionCardView(
                                        "Hunt Rank",
                                        huntMastery.currentRank().displayName(),
                                        huntMastery.masteryPoints() + " mastery · tier " + huntMastery.baseVisualTier(),
                                        huntMastery.currentRank().accentColor()),
                                new ProgressionCardView(
                                        "Compendium",
                                        huntMastery.discoveredPreyRoleIds().size() + " prey",
                                        huntMastery.uniqueContractsCompleted() + " contracts · " + huntMastery.eliteCompletionCount() + " elite claims",
                                        "#ef4444"),
                                new ProgressionCardView(
                                        "Archetype Mastery",
                                        huntMastery.archetypeCompletionCounts().size() + " tracked",
                                        summarizeTopArchetype(huntMastery),
                                        "#dc2626"),
                                new ProgressionCardView(
                                        "Next Rank",
                                        huntMastery.nextRank() != null ? huntMastery.nextRank().displayName() : "Max rank",
                                        huntMastery.nextRank() != null
                                                ? huntMastery.masteryToNextRank() + " mastery remaining"
                                                : "All hunt mastery milestones claimed",
                                        "#f59e0b"),
                                new ProgressionCardView(
                                        "Preparation",
                                        huntLoadout.preparationDisplayName(),
                                        huntLoadout.modeDisplayName() + " · Open the Hunt Briefing to preview and change.",
                                        "#f59e0b"),
                                new ProgressionCardView(
                                        "Recent Reward",
                                        huntMastery.lastRewardedPreyRoleId() != null
                                                ? NightHuntPresentationText.preyName(huntMastery.lastRewardedPreyRoleId())
                                                : "No recent hunt",
                                        summarizeRecentHuntReward(huntMastery),
                                        "#f59e0b"))),
                new ProgressionSectionView(
                        "heat",
                        "Heat",
                        "Masquerade Heat",
                        "No masquerade telemetry has been recorded yet.",
                        buildHeatCards(
                                masquerade,
                                masqueradeHeatService.policy(),
                                bloodAffinities,
                                lineageEvaluations,
                                continuity)));
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
    private static String summarizeRecentHuntReward(@Nonnull com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot mastery) {
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
        return parts.isEmpty() ? "No tangible bonus recorded." : String.join(" · ", parts);
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
    static List<ProgressionCardView> buildHeatCards(@Nonnull MasqueradeHeatSnapshot masquerade,
                                                    @Nonnull MasqueradeHeatPolicy policy,
                                                    @Nonnull Map<String, Integer> bloodAffinities,
                                                    @Nonnull List<VampiricLineageEvaluation> lineageEvaluations,
                                                    @Nonnull NightHuntContinuitySnapshot continuity) {
        MasqueradeHeatThresholdText.ThresholdView nextThreshold =
                MasqueradeHeatThresholdText.nextThreshold(masquerade, policy);
        LineageWindowOpportunity.View opportunity = LineageWindowOpportunity.resolve(
                masquerade,
                bloodAffinities,
                lineageEvaluations);
        PressureOutlookText.View pressureOutlook = PressureOutlookText.resolve(continuity);
        PressureDriversText.View pressureDrivers = PressureDriversText.resolve(continuity);
        return List.of(
                new ProgressionCardView(
                        "Current Exposure",
                        formatExposureLevel(masquerade.exposureLevel()) + " · " + formatHeat(masquerade.heat()) + " heat",
                        "Last updated: " + formatInstant(masquerade.lastUpdatedAtMs(), "Never"),
                        heatAccent(masquerade.exposureLevel())),
                new ProgressionCardView(
                        "Next Threshold",
                        nextThreshold.value(),
                        nextThreshold.detail(),
                        nextThreshold.accentColor()),
                new ProgressionCardView(
                        "Current Risk",
                        currentRiskValue(masquerade),
                        currentRiskDetail(masquerade),
                        masquerade.progressionLocked() ? "#ef4444" : heatAccent(masquerade.exposureLevel())),
                new ProgressionCardView(
                        "Pressure Outlook",
                        pressureOutlook.value(),
                        pressureOutlook.detail(),
                        pressureOutlook.accentColor()),
                new ProgressionCardView(
                        "Pressure Drivers",
                        pressureDrivers.value(),
                        pressureDrivers.detail(),
                        pressureDrivers.accentColor()),
                new ProgressionCardView(
                        "Current Opportunity",
                        opportunity.value(),
                        opportunity.detail(),
                        opportunity.accentColor()));
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
                selectedLineage != null ? selectedLineage.clan().accentColor() : "#ef4444"));
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
                        : availableLineages > 0 ? "#22c55e" : "#f97316"));
        cards.add(new ProgressionCardView(
                "Respec Count",
                Integer.toString(lineageRespecCount),
                "Eligible lineages: " + availableLineages + " / " + lineageEvaluations.size(),
                "#f97316"));
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
                    evaluation.clan().accentColor()));
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
                ? " " + masquerade.strikeCount() + " strike" + (masquerade.strikeCount() == 1 ? "" : "s") + " tracked."
                : "";
        if (masquerade.progressionLocked()) {
            return "Low-heat progression is closed right now. Shed exposure before chasing more gated unlocks." + strikeDetail;
        }
        return switch (masquerade.exposureLevel()) {
            case QUIET -> strikeDetail.isBlank()
                    ? "No active hunter route pressure is building right now."
                    : "Heat is low, but past exposure still keeps hunters alert." + strikeDetail;
            case WATCHED -> "Hunters are watching your routes. Another loud spike will push you toward pursuit." + strikeDetail;
            case HUNTED -> "Your trail is hot enough to draw pursuit. Cooling now protects future low-heat unlocks." + strikeDetail;
            case BREACHED -> "Exposure is blown wide open. Staying here makes every low-heat route harder to hold." + strikeDetail;
        };
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
