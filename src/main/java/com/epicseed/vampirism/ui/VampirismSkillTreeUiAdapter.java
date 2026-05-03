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
import com.epicseed.vampirism.domain.lineage.VampiricLineageDefinition;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeExposureLevel;
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
                        "Lineage",
                        "No lineage details are available.",
                        buildLineageCards(uuid, store, lineageEvaluations, selectedLineage, availableLineages)),
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
                        "heat",
                        "Heat",
                        "Masquerade Heat",
                        "No masquerade telemetry has been recorded yet.",
                        List.of(
                                new ProgressionCardView(
                                        "Masquerade Heat",
                                        formatHeat(masquerade.heat()),
                                        "Last updated: " + formatInstant(
                                                masquerade.lastUpdatedAtMs(),
                                                "Never"),
                                        "#f59e0b"),
                                new ProgressionCardView(
                                        "Strikes",
                                        Integer.toString(masquerade.strikeCount()),
                                        "Accumulated masquerade strike count.",
                                        "#dc2626"),
                                new ProgressionCardView(
                                        "Hunter Pressure",
                                        Integer.toString(masquerade.hunterPressure()),
                                        formatExposureLevel(masquerade.exposureLevel())
                                                + (masquerade.progressionLocked()
                                                ? " · progression gating active"
                                                : " · progression gating clear"),
                                        "#2563eb"))));
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
        String[] tokens = value.trim().split("[-_\\s]+");
        StringBuilder out = new StringBuilder();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(token.charAt(0)));
            if (token.length() > 1) {
                out.append(token.substring(1).toLowerCase());
            }
        }
        return out.length() == 0 ? fallback : out.toString();
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
    private static String formatExposureLevel(@Nonnull MasqueradeExposureLevel level) {
        return switch (level) {
            case QUIET -> "Quiet";
            case WATCHED -> "Watched";
            case HUNTED -> "Hunted";
            case BREACHED -> "Breached";
        };
    }

    @Nonnull
    private static List<ProgressionCardView> buildLineageCards(@Nonnull UUID uuid,
                                                               @Nonnull VampirePlayerStateStore store,
                                                               @Nonnull List<VampiricLineageEvaluation> lineageEvaluations,
                                                               @Nullable VampiricLineageEvaluation selectedLineage,
                                                               long availableLineages) {
        ArrayList<ProgressionCardView> cards = new ArrayList<>();
        cards.add(new ProgressionCardView(
                "Current Lineage",
                selectedLineage != null
                        ? selectedLineage.definition().displayName()
                        : formatIdentifier(store.getLineageId(uuid), "Unbound"),
                (selectedLineage != null ? selectedLineage.clan().displayName() : "No clan selected")
                        + " · Unlocked at: " + formatInstant(store.getLineageUnlockedAtMs(uuid), "Not yet unlocked"),
                selectedLineage != null ? selectedLineage.clan().accentColor() : "#ef4444"));
        cards.add(new ProgressionCardView(
                "Respec Count",
                Integer.toString(store.getLineageRespecCount(uuid)),
                "Eligible lineages: " + availableLineages + " / " + lineageEvaluations.size(),
                "#f97316"));
        for (VampiricLineageEvaluation evaluation : lineageEvaluations) {
            String status = evaluation.selected() ? "Selected" : evaluation.available() ? "Available" : "Locked";
            String detail = lineagePerkSummary(evaluation.definition());
            if (!evaluation.available()) {
                detail = detail + " · " + String.join(" ", evaluation.blockingReasons());
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
}
