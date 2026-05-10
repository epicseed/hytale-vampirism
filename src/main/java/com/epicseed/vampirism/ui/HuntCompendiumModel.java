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
import com.epicseed.epiccore.vampirism.domain.player.NamedHuntProgress;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.age.VampiricAgeTierService;
import com.epicseed.vampirism.domain.hunt.NightHuntContracts;
import com.epicseed.vampirism.domain.hunt.NightHuntMasterySnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout;
import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;
import com.epicseed.vampirism.domain.hunt.NightHuntProgressionService;
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
                             @Nonnull String statusText,
                             boolean selected,
                             boolean previewed) {
    }

    private final NightHuntMasterySnapshot mastery;
    private final NamedHuntProgress progress;
    private final List<NightHuntSpawnRegistry.SpawnOption> preyCatalogue;
    private final List<NightHuntPreparedLoadout> availableLoadouts;
    private final NightHuntPreparedLoadout currentLoadout;
    private final NightHuntPreparedLoadout previewLoadout;
    private final HuntCompendiumNextRiteResolver.NextRite nextRite;
    private final LineageWindowOpportunity.View lineageWindow;
    private final VampiricAgeTierSnapshot ageTierSnapshot;
    private final String nextThresholdText;
    private final Tab selectedTab;

    private HuntCompendiumModel(@Nonnull NightHuntMasterySnapshot mastery,
                                @Nonnull NamedHuntProgress progress,
                                @Nonnull List<NightHuntSpawnRegistry.SpawnOption> preyCatalogue,
                                 @Nonnull List<NightHuntPreparedLoadout> availableLoadouts,
                                    @Nonnull NightHuntPreparedLoadout currentLoadout,
                                    @Nonnull NightHuntPreparedLoadout previewLoadout,
                                    @Nullable HuntCompendiumNextRiteResolver.NextRite nextRite,
                                    @Nullable LineageWindowOpportunity.View lineageWindow,
                                    @Nonnull VampiricAgeTierSnapshot ageTierSnapshot,
                                    @Nullable String nextThresholdText,
                                    @Nonnull Tab selectedTab) {
        this.mastery = mastery;
        this.progress = progress;
        this.preyCatalogue = preyCatalogue;
        this.availableLoadouts = availableLoadouts;
        this.currentLoadout = currentLoadout;
        this.previewLoadout = previewLoadout;
        this.nextRite = nextRite;
        this.lineageWindow = lineageWindow;
        this.ageTierSnapshot = ageTierSnapshot;
        this.nextThresholdText = nextThresholdText;
        this.selectedTab = selectedTab;
    }

    @Nonnull
    static HuntCompendiumModel create(@Nonnull UUID uuid,
                                      @Nonnull Tab selectedTab,
                                      @Nullable String previewPreparationId,
                                      @Nullable HuntCompendiumNextRiteResolver.NextRite nextRite,
                                      @Nullable LineageWindowOpportunity.View lineageWindow,
                                      @Nullable String nextThresholdText) {
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
        return new HuntCompendiumModel(
                NightHuntProgressionService.snapshot(progress),
                progress,
                List.copyOf(preyCatalogue),
                List.copyOf(availableLoadouts),
                currentLoadout,
                previewLoadout,
                nextRite,
                lineageWindow,
                VampiricAgeTierService.snapshot(uuid),
                nextThresholdText,
                selectedTab);
    }

    @Nonnull
    String title() {
        return "Night Hunt Briefing";
    }

    @Nonnull
    String subtitle() {
        return mastery.currentRank().displayName()
                + " · " + mastery.masteryPoints() + " mastery"
                + " · " + mastery.discoveredPreyRoleIds().size() + "/" + preyCatalogue.size() + " prey logged";
    }

    @Nonnull
    String preparedLoadoutText() {
        return currentLoadout.preparationDisplayName() + " · " + currentLoadout.modeDisplayName();
    }

    @Nonnull
    String nextRankText() {
        return mastery.nextRank() != null
                ? mastery.nextRank().displayName() + " in " + mastery.masteryToNextRank()
                : "Maximum rank claimed";
    }

    @Nonnull
    String overviewSummaryText() {
        return String.join("\n",
                "Contracts completed: " + mastery.totalCompletions() + " · unique contracts " + mastery.uniqueContractsCompleted(),
                "Prepared for next hunt: " + currentLoadout.preparationDisplayName() + " into " + currentLoadout.modeDisplayName() + ".",
                "Objective focus: " + currentLoadout.objectiveText(),
                "Elite prey claimed: " + mastery.eliteCompletionCount());
    }

    @Nonnull
    String overviewContinuityText() {
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Threat: " + (progress.worldThreatLevel > 0
                ? NightHuntPresentationText.humanize(threatName()) + " (" + progress.worldThreatLevel + ")"
                : "Quiet"));
        if (progress.pendingChainPreyRoleId != null) {
            int preyMemory = progress.preyAdaptationLevels.getOrDefault(progress.pendingChainPreyRoleId, 0);
            if (preyMemory > 0) {
                lines.add("Prey memory: " + NightHuntPresentationText.preyName(progress.pendingChainPreyRoleId)
                        + " (" + preyMemory + ")");
            }
        }
        if (progress.pendingChainModeId != null) {
            int modeMemory = progress.modeSuccessCounts.getOrDefault(progress.pendingChainModeId, 0) / 2;
            if (modeMemory > 0) {
                lines.add("Behavior read: " + NightHuntPresentationText.contractName(null, progress.pendingChainModeId)
                        + " (" + modeMemory + ")");
            }
        }
        if (progress.activeChainId != null && progress.activeChainStep > 0) {
            lines.add("Chain: " + NightHuntPresentationText.humanize(progress.activeChainId) + " " + roman(progress.activeChainStep));
        }
        lines.add("Current streak: " + progress.successStreak + " success / " + progress.failureStreak + " failure");
        return String.join("\n", lines);
    }

    @Nonnull
    String overviewRewardText() {
        if (mastery.lastRewardedAtMs() <= 0L) {
            return "No hunt rewards have been recorded yet.";
        }
        ArrayList<String> parts = new ArrayList<>();
        if (mastery.lastRewardSkillPoints() > 0) {
            parts.add("+" + mastery.lastRewardSkillPoints() + " skill point" + (mastery.lastRewardSkillPoints() == 1 ? "" : "s"));
        }
        if (mastery.lastRewardMasteryPoints() > 0) {
            parts.add("+" + mastery.lastRewardMasteryPoints() + " mastery");
        }
        if (mastery.lastRewardBlood() > 0) {
            parts.add("+" + mastery.lastRewardBlood() + " blood");
        }
        if (mastery.lastRewardAgeProgress() > 0) {
            parts.add("+" + mastery.lastRewardAgeProgress() + " age progress");
        }
        if (mastery.lastRewardAffinityAmount() > 0 && mastery.lastRewardAffinityId() != null) {
            parts.add("Affinity " + NightHuntPresentationText.humanize(mastery.lastRewardAffinityId())
                    + " +" + mastery.lastRewardAffinityAmount());
        }
        String source = mastery.lastRewardedPreyRoleId() != null
                ? NightHuntPresentationText.preyName(mastery.lastRewardedPreyRoleId())
                : mastery.lastRewardedContractId() != null
                ? NightHuntPresentationText.contractTargetSummary(mastery.lastRewardedContractId())
                : "Unknown prey";
        String rewards = parts.isEmpty() ? "No tangible bonus recorded." : String.join(" · ", parts);
        String milestone = mastery.lastRewardedArchetypeMilestoneId() != null
                ? "\nMilestone: " + NightHuntPresentationText.humanize(mastery.lastRewardedArchetypeMilestoneId())
                : "";
        String outcome = progress.lastOutcomeId != null
                ? "\nLast outcome: " + NightHuntPresentationText.humanize(progress.lastOutcomeId)
                : "";
        String nextStep = overviewGuidanceText(nextRite, lineageWindow, ageTierSnapshot, nextThresholdText);
        return "Most recent prey: " + source + "\n" + rewards + milestone + outcome + nextStep;
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
                    status,
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
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Trail tier delta: " + signedDelta(previewLoadout.visualTierDelta()));
        lines.add("Route delta: " + signedDelta(previewLoadout.waypointTargetAdjustment()) + " waypoint"
                + (Math.abs(previewLoadout.waypointTargetAdjustment()) == 1 ? "" : "s"));
        lines.add("Prey lifetime: x" + trimMultiplier(previewLoadout.preyLifetimeMultiplier()));
        if (previewLoadout.requiredOwnerHits() > 0) {
            lines.add("Prime requirement: " + previewLoadout.requiredOwnerHits() + " owner hit"
                    + (previewLoadout.requiredOwnerHits() == 1 ? "" : "s"));
        } else {
            lines.add("Prime requirement: none");
        }
        if (previewLoadout.pressureSeconds() > 0f) {
            lines.add("Pressure hold: " + Math.round(previewLoadout.pressureSeconds()) + "s within "
                    + trimRadius(previewLoadout.pressureRadius()) + "m");
        } else {
            lines.add("Pressure hold: none");
        }
        return String.join("\n", lines);
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
        NightHuntProgressionRegistry.Snapshot registry = NightHuntProgressionRegistry.get().snapshot();
        ArrayList<String> lines = new ArrayList<>();
        for (NightHuntProgressionRegistry.ArchetypeDefinition archetype : registry.archetypes()) {
            int completions = mastery.archetypeCompletionCounts().getOrDefault(archetype.id(), 0);
            NightHuntProgressionRegistry.ArchetypeMilestone achieved = archetype.achievedMilestone(completions);
            NightHuntProgressionRegistry.ArchetypeMilestone next = archetype.nextMilestone(completions);
            StringBuilder line = new StringBuilder()
                    .append(archetype.displayName())
                    .append(" · ")
                    .append(completions)
                    .append(" claim")
                    .append(completions == 1 ? "" : "s");
            if (achieved != null) {
                line.append(" · ").append(achieved.displayName());
            }
            if (next != null) {
                line.append(" · next in ").append(Math.max(0, next.killsRequired() - completions));
            } else {
                line.append(" · mastered");
            }
            lines.add(line.toString());
        }
        return String.join("\n", lines);
    }

    @Nonnull
    String recordsContractText() {
        ArrayList<Map.Entry<String, Integer>> entries = new ArrayList<>(progress.contractCompletionCounts.entrySet());
        entries.sort(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER));
        ArrayList<String> lines = new ArrayList<>();
        if (progress.activeContractId != null) {
            lines.add("Active: " + NightHuntPresentationText.contractTargetSummary(progress.activeContractId)
                    + " · stage " + Math.max(1, progress.activeStep));
        }
        if (progress.activeChainId != null && progress.activeChainStep > 0) {
            lines.add("Chain: " + NightHuntPresentationText.humanize(progress.activeChainId) + " " + roman(progress.activeChainStep));
        }
        if (entries.isEmpty()) {
            lines.add("No contracts have been completed yet.");
        } else {
            for (int i = 0; i < Math.min(entries.size(), 8); i++) {
                Map.Entry<String, Integer> entry = entries.get(i);
                lines.add(NightHuntPresentationText.contractTargetSummary(entry.getKey())
                        + " · " + entry.getValue() + " completion" + (entry.getValue() == 1 ? "" : "s"));
            }
        }
        return String.join("\n", lines);
    }

    @Nonnull
    String quarryText() {
        ArrayList<String> lines = new ArrayList<>();
        for (NightHuntSpawnRegistry.SpawnOption option : preyCatalogue) {
            boolean discovered = mastery.discoveredPreyRoleIds().contains(option.roleId());
            String line = (discovered ? option.displayName() : "Unknown prey")
                    + " · " + NightHuntPresentationText.humanize(option.preyFamily())
                    + " / " + NightHuntPresentationText.archetypeName(option.archetype())
                    + " · tier " + option.visualTier()
                    + (option.elite() ? " · elite" : "")
                    + (discovered ? " · logged" : " · undiscovered");
            lines.add(line);
        }
        return String.join("\n", lines);
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
