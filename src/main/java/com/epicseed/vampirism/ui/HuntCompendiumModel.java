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
                             boolean selected,
                             boolean previewed) {
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
                                    @Nonnull Tab selectedTab) {
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
        NightHuntPreparationAffinityContent.PreparationAffinity focus =
                NightHuntPreparationAffinityContent.focusForPreparation(currentLoadout.preparationId());
        return currentLoadout.preparationDisplayName() + " · " + currentLoadout.modeDisplayName()
                + (focus != null ? " · " + focus.laneLabel() : "");
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
                "Affinity lane: " + preparationRecapText(currentLoadout),
                "Objective focus: " + currentLoadout.objectiveText(),
                "Elite prey claimed: " + mastery.eliteCompletionCount());
    }

    @Nonnull
    String overviewContinuityText() {
        ArrayList<String> lines = new ArrayList<>();
        HuntCrackdownText.View crackdown = HuntCrackdownText.resolve(persistedNightHuntState, continuity, progress);
        HuntCasefileText.View casefile = HuntCasefileText.resolve(persistedNightHuntState, progress);
        if (persistedNightHuntState.cooldownRemainingMs(System.currentTimeMillis()) > 0L || crackdown.active()) {
            lines.add("Next window: " + crackdown.value());
            lines.add(crackdown.detail());
        }
        if (casefile.active()) {
            lines.add("Casefile: " + casefile.value());
            lines.add(casefile.detail());
        } else {
            String lastClearedCasefile = NightHuntCasefileService.lastClearedCasefileDisplayName(progress);
            if (lastClearedCasefile != null) {
                lines.add("Last cleared casefile: " + lastClearedCasefile);
                lines.add("Hunters are pivoting away from that file before they reopen the same route.");
            }
        }
        lines.add("Threat: " + (continuity.worldThreatLevel() > 0
                ? continuity.worldThreatName() + " (" + continuity.worldThreatLevel() + ")"
                : "Quiet"));
        if (continuity.preyMemoryName() != null && continuity.preyMemoryLevel() > 0) {
            lines.add("Prey memory: " + continuity.preyMemoryName() + " (" + continuity.preyMemoryLevel() + ")");
        }
        if (continuity.behaviorMemoryName() != null && continuity.behaviorMemoryLevel() > 0) {
            lines.add("Behavior read: " + continuity.behaviorMemoryName() + " (" + continuity.behaviorMemoryLevel() + ")");
        }
        if (continuity.activeChainName() != null && continuity.activeChainStep() > 0) {
            lines.add("Chain: " + continuity.activeChainName() + " " + roman(continuity.activeChainStep()));
        }
        lines.add("Current streak: " + continuity.successStreak() + " success / " + continuity.failureStreak() + " failure");
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
        ArrayList<String> lines = new ArrayList<>();
        lines.add("Most recent prey: " + source);
        lines.add(parts.isEmpty() ? "No tangible bonus recorded." : String.join(" · ", parts));
        String laneReadout = recentRewardLaneText(mastery);
        if (!laneReadout.isBlank()) {
            lines.add(laneReadout);
        }
        if (mastery.lastRewardedArchetypeMilestoneId() != null) {
            lines.add("Milestone: " + NightHuntPresentationText.humanize(mastery.lastRewardedArchetypeMilestoneId()));
        }
        if (progress.lastOutcomeId != null) {
            lines.add("Last outcome: " + NightHuntPresentationText.humanize(progress.lastOutcomeId));
        }
        String nextStep = overviewGuidanceText(nextRite, lineageWindow, ageTierSnapshot, nextThresholdText);
        return String.join("\n", lines) + nextStep;
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
                    preparationFocusLabel(loadout.preparationId()),
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
        NightHuntPreparationAffinityContent.PreparationAffinity focus =
                NightHuntPreparationAffinityContent.focusForPreparation(previewLoadout.preparationId());
        if (focus != null) {
            lines.add("Affinity focus: " + focus.preyFamilyDisplayName());
            lines.add("Lane bonus: " + focus.bonusText());
            lines.add("Affinity path: " + focus.focusText());
        } else {
            lines.add("Affinity focus: none");
        }
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
