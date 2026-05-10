package com.epicseed.vampirism.ui;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.progression.VampiricProgressionProofs;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;
import com.epicseed.vampirism.domain.ritual.VampiricRitualEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;

final class HuntCompendiumNextRiteResolver {

    private static final List<String> PRIORITY_RITUAL_IDS = List.of(
            VampiricRitualRegistry.VEIL_OF_NIGHT_RITUAL_ID,
            VampiricRitualRegistry.SUMMON_FAMILIAR_RITUAL_ID,
            VampiricRitualRegistry.SOUL_EXCHANGE_RITUAL_ID,
            VampiricRitualRegistry.MARK_PREY_RITUAL_ID);

    record NextRite(@Nonnull String ritualName, @Nonnull String guidance) {
    }

    private final VampiricRitualService ritualService;

    HuntCompendiumNextRiteResolver(@Nonnull VampiricRitualService ritualService) {
        this.ritualService = ritualService;
    }

    @Nullable
    NextRite resolve(@Nonnull UUID uuid, @Nonnull VampiricRitualContext context) {
        for (String ritualId : orderedCandidateIds()) {
            VampiricRitualDefinition definition = ritualService.registry().definition(ritualId).orElse(null);
            if (definition == null) {
                continue;
            }
            VampiricRitualEvaluation evaluation = ritualService.evaluate(uuid, ritualId, context);
            if (evaluation.completed()) {
                continue;
            }
            return new NextRite(definition.displayName(), guidance(evaluation));
        }
        return null;
    }

    @Nonnull
    private List<String> orderedCandidateIds() {
        LinkedHashSet<String> ordered = new LinkedHashSet<>(PRIORITY_RITUAL_IDS);
        for (String ritualId : ritualService.registry().definitions().keySet()) {
            if (!VampiricRitualRegistry.AWAKENING_RITUAL_ID.equals(ritualId)) {
                ordered.add(ritualId);
            }
        }
        return List.copyOf(ordered);
    }

    @Nonnull
    private static String guidance(@Nonnull VampiricRitualEvaluation evaluation) {
        if (evaluation.active()) {
            return "Already active on your circle.";
        }
        if (evaluation.blockingReasons().isEmpty()) {
            return "Ready now. Return to a ritual anchor and invoke it.";
        }
        String reason = primaryReason(evaluation.blockingReasons());
        if (reason.startsWith("missing_proof:")) {
            return VampiricProgressionProofs.blockingLabel(reason.substring("missing_proof:".length())) + ".";
        }
        VampiricRitualDefinition.AffinityRequirement affinityRequirement =
                VampiricRitualDefinition.AffinityRequirement.fromBlockingReason(reason);
        if (affinityRequirement != null) {
            return "Earn " + affinityRequirement.requirementLabel() + " affinity from night hunt rewards.";
        }
        if (reason.startsWith("missing_skill:")) {
            return "Unlock " + NightHuntPresentationText.humanize(reason.substring("missing_skill:".length())) + " first.";
        }
        if (reason.startsWith("min_completed_night_hunts:")) {
            return "Complete " + reason.substring("min_completed_night_hunts:".length()) + " night hunts first.";
        }
        if (reason.startsWith("required_age_tier:")) {
            return "Reach age tier " + NightHuntPresentationText.humanize(reason.substring("required_age_tier:".length())) + ".";
        }
        if (reason.startsWith("min_blood:")) {
            return "Hold " + reason.substring("min_blood:".length()) + " blood before the rite.";
        }
        if (reason.startsWith("missing_tag:")) {
            return switch (reason.substring("missing_tag:".length())) {
                case VampiricRitualRegistry.TAG_NIGHT -> "Perform it at night.";
                case VampiricRitualRegistry.TAG_ANCIENT_COFFIN -> "Stand beside an ancient coffin.";
                case VampiricRitualRegistry.TAG_INFECTED -> "The rite needs the infected state.";
                default -> "Prepare the omen " + NightHuntPresentationText.humanize(reason.substring("missing_tag:".length()))
                        + ".";
            };
        }
        return NightHuntPresentationText.humanize(reason);
    }

    @Nonnull
    private static String primaryReason(@Nonnull List<String> reasons) {
        String best = reasons.get(0);
        int bestPriority = reasonPriority(best);
        for (int i = 1; i < reasons.size(); i++) {
            String candidate = reasons.get(i);
            int priority = reasonPriority(candidate);
            if (priority < bestPriority) {
                best = candidate;
                bestPriority = priority;
            }
        }
        return best;
    }

    private static int reasonPriority(@Nonnull String reason) {
        if (reason.startsWith("missing_proof:")) {
            return 0;
        }
        if (VampiricRitualDefinition.AffinityRequirement.fromBlockingReason(reason) != null) {
            return 1;
        }
        if (reason.startsWith("missing_skill:")) {
            return 2;
        }
        if (reason.startsWith("min_completed_night_hunts:")) {
            return 3;
        }
        if (reason.startsWith("required_age_tier:")) {
            return 4;
        }
        if (reason.startsWith("min_blood:")) {
            return 5;
        }
        if (reason.startsWith("missing_tag:")) {
            String tag = reason.substring("missing_tag:".length()).toLowerCase(Locale.ROOT);
            return VampiricRitualRegistry.TAG_NIGHT.equals(tag) ? 7 : 6;
        }
        return 8;
    }
}
