package com.epicseed.vampirism.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;

final class LineageRequirementText {

    private static final String REQUIRED_AGE_TIER_REASON_PREFIX = "required_age_tier:";
    private static final String MIN_COMPLETED_NIGHT_HUNTS_REASON_PREFIX = "min_completed_night_hunts:";
    private static final String MISSING_SKILL_REASON_PREFIX = "missing_skill:";
    private static final String MISSING_COMPLETED_RITUAL_REASON_PREFIX = "missing_completed_ritual:";
    private static final String MAX_MASQUERADE_HEAT_REASON_PREFIX = "max_masquerade_heat:";

    private LineageRequirementText() {
    }

    @Nonnull
    static String blockerSummary(@Nonnull VampiricLineageEvaluation evaluation,
                                 @Nonnull Map<String, Integer> bloodAffinities) {
        return evaluation.blockingReasons().stream()
                .map(reason -> actionText(reason, bloodAffinities, true))
                .reduce((left, right) -> left + " · " + right)
                .orElse("");
    }

    @Nullable
    static String primaryActionText(@Nonnull VampiricLineageEvaluation evaluation,
                                    @Nonnull Map<String, Integer> bloodAffinities) {
        String reason = primaryReason(evaluation.blockingReasons());
        return reason != null ? actionText(reason, bloodAffinities, false) : null;
    }

    static boolean hasAffinityAsMainRemainingBlocker(@Nonnull VampiricLineageEvaluation evaluation) {
        List<String> nonHeatReasons = evaluation.blockingReasons().stream()
                .filter(reason -> !reason.startsWith(MAX_MASQUERADE_HEAT_REASON_PREFIX))
                .toList();
        return !nonHeatReasons.isEmpty()
                && nonHeatReasons.stream().allMatch(reason ->
                VampiricRitualDefinition.AffinityRequirement.fromBlockingReason(reason) != null);
    }

    @Nullable
    private static String primaryReason(@Nonnull List<String> reasons) {
        return reasons.stream()
                .min(Comparator
                        .comparingInt(LineageRequirementText::reasonPriority)
                        .thenComparing(String::compareTo))
                .orElse(null);
    }

    @Nonnull
    private static String actionText(@Nonnull String reason,
                                     @Nonnull Map<String, Integer> bloodAffinities,
                                     boolean capitalize) {
        String action = actionText(reason, bloodAffinities);
        if (!capitalize || action.isEmpty()) {
            return action;
        }
        return action.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + action.substring(1);
    }

    @Nonnull
    private static String actionText(@Nonnull String reason,
                                     @Nonnull Map<String, Integer> bloodAffinities) {
        VampiricRitualDefinition.AffinityRequirement affinityRequirement =
                VampiricRitualDefinition.AffinityRequirement.fromBlockingReason(reason);
        if (affinityRequirement != null) {
            int currentAffinity = Math.max(0, bloodAffinities.getOrDefault(affinityRequirement.affinityId(), 0));
            return "raise " + affinityRequirement.affinityDisplayName()
                    + " affinity to " + affinityRequirement.minAmount()
                    + " (currently " + currentAffinity + "/" + affinityRequirement.minAmount() + ")";
        }
        if (reason.startsWith(MISSING_COMPLETED_RITUAL_REASON_PREFIX)) {
            return "complete " + NightHuntPresentationText.humanize(
                    reason.substring(MISSING_COMPLETED_RITUAL_REASON_PREFIX.length()));
        }
        if (reason.startsWith(MISSING_SKILL_REASON_PREFIX)) {
            return "unlock " + NightHuntPresentationText.humanize(reason.substring(MISSING_SKILL_REASON_PREFIX.length()));
        }
        if (reason.startsWith(REQUIRED_AGE_TIER_REASON_PREFIX)) {
            return "reach " + NightHuntPresentationText.humanize(reason.substring(REQUIRED_AGE_TIER_REASON_PREFIX.length()))
                    + " age tier";
        }
        if (reason.startsWith(MIN_COMPLETED_NIGHT_HUNTS_REASON_PREFIX)) {
            String count = reason.substring(MIN_COMPLETED_NIGHT_HUNTS_REASON_PREFIX.length());
            return "complete " + count + " night hunt" + ("1".equals(count) ? "" : "s");
        }
        if (reason.startsWith(MAX_MASQUERADE_HEAT_REASON_PREFIX)) {
            return "hold masquerade heat at "
                    + reason.substring(MAX_MASQUERADE_HEAT_REASON_PREFIX.length()) + " or lower";
        }
        return "clear " + NightHuntPresentationText.humanize(reason);
    }

    private static int reasonPriority(@Nonnull String reason) {
        if (VampiricRitualDefinition.AffinityRequirement.fromBlockingReason(reason) != null) {
            return 0;
        }
        if (reason.startsWith(MISSING_COMPLETED_RITUAL_REASON_PREFIX)) {
            return 1;
        }
        if (reason.startsWith(MISSING_SKILL_REASON_PREFIX)) {
            return 2;
        }
        if (reason.startsWith(REQUIRED_AGE_TIER_REASON_PREFIX)) {
            return 3;
        }
        if (reason.startsWith(MIN_COMPLETED_NIGHT_HUNTS_REASON_PREFIX)) {
            return 4;
        }
        if (reason.startsWith(MAX_MASQUERADE_HEAT_REASON_PREFIX)) {
            return 5;
        }
        return 6;
    }
}
