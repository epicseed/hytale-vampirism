package com.epicseed.vampirism.ui;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;

final class LineageWindowOpportunity {

    private LineageWindowOpportunity() {
    }

    @Nonnull
    static View resolve(@Nonnull MasqueradeHeatSnapshot masquerade,
                        @Nonnull Map<String, Integer> bloodAffinities,
                        @Nonnull List<VampiricLineageEvaluation> lineageEvaluations) {
        List<VampiricLineageEvaluation> heatSensitive = lineageEvaluations.stream()
                .filter(evaluation -> !evaluation.selected())
                .filter(evaluation -> evaluation.definition().requirements().maxMasqueradeHeat() != null)
                .toList();
        if (heatSensitive.isEmpty()) {
            return new View(
                    "No heat gate wired",
                    "Current lineage routes are not using masquerade heat as a progression gate yet.",
                    "#64748b");
        }

        double heat = masquerade.heat();
        VampiricLineageEvaluation blocked = heatSensitive.stream()
                .filter(evaluation -> heat > evaluation.definition().requirements().maxMasqueradeHeat())
                .min(Comparator
                        .comparingDouble((VampiricLineageEvaluation evaluation) ->
                                heat - evaluation.definition().requirements().maxMasqueradeHeat())
                        .thenComparing(evaluation -> evaluation.definition().displayName()))
                .orElse(null);
        if (blocked != null) {
            double cap = blocked.definition().requirements().maxMasqueradeHeat();
            return new View(
                    blocked.definition().displayName() + " blocked",
                    "Cool " + formatHeat(heat - cap) + " heat to reopen the " + formatHeat(cap) + " cap.",
                    "#f97316");
        }

        VampiricLineageEvaluation available = heatSensitive.stream()
                .filter(VampiricLineageEvaluation::available)
                .min(Comparator
                        .comparingDouble((VampiricLineageEvaluation evaluation) ->
                                evaluation.definition().requirements().maxMasqueradeHeat())
                        .thenComparing(evaluation -> evaluation.definition().displayName()))
                .orElse(null);
        if (available != null) {
            double cap = available.definition().requirements().maxMasqueradeHeat();
            return new View(
                    available.definition().displayName() + " ready",
                    "Stay at or below " + formatHeat(cap) + " heat to keep it claimable.",
                    "#22c55e");
        }

        VampiricLineageEvaluation futureWindow = heatSensitive.stream()
                .filter(evaluation -> heat <= evaluation.definition().requirements().maxMasqueradeHeat())
                .min(Comparator
                        .comparingInt((VampiricLineageEvaluation evaluation) -> evaluation.blockingReasons().size())
                        .thenComparingDouble(evaluation -> evaluation.definition().requirements().maxMasqueradeHeat())
                        .thenComparing(evaluation -> evaluation.definition().displayName()))
                .orElse(heatSensitive.get(0));
        double cap = futureWindow.definition().requirements().maxMasqueradeHeat();
        String detail = "Stay at or below " + formatHeat(cap) + " heat while you finish its other requirements.";
        if (LineageRequirementText.hasAffinityAsMainRemainingBlocker(futureWindow)) {
            String action = LineageRequirementText.primaryActionText(futureWindow, bloodAffinities);
            if (action != null) {
                detail = "Stay at or below " + formatHeat(cap) + " heat while you " + action + ".";
            }
        }
        return new View(
                futureWindow.definition().displayName() + " pending",
                detail,
                "#a855f7");
    }

    @Nonnull
    private static String formatHeat(double heat) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0d, heat));
    }

    record View(@Nonnull String value, @Nonnull String detail, @Nonnull String accentColor) {

        @Nonnull
        String compactText() {
            return "Lineage window: " + value + " - " + lowerCaseFirst(detail);
        }

        @Nonnull
        private static String lowerCaseFirst(@Nonnull String text) {
            if (text.isEmpty()) {
                return text;
            }
            return text.substring(0, 1).toLowerCase(Locale.ROOT) + text.substring(1);
        }
    }
}
