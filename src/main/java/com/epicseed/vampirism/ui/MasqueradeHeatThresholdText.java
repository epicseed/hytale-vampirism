package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatPolicy;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatSnapshot;

final class MasqueradeHeatThresholdText {

    private MasqueradeHeatThresholdText() {
    }

    @Nonnull
    static ThresholdView nextThreshold(@Nonnull MasqueradeHeatSnapshot masquerade,
                                       @Nonnull MasqueradeHeatPolicy policy) {
        double heat = masquerade.heat();
        ArrayList<ThresholdView> thresholds = new ArrayList<>();
        addThreshold(thresholds, "Watched", policy.watchedThreshold(), heat,
                "hunter attention turns your way.", "#f59e0b");
        addThreshold(thresholds, "Hunted", policy.huntedThreshold(), heat,
                "hunters escalate into active pursuit.", "#f97316");
        addThreshold(thresholds, "Progression Lock", policy.progressionLockThreshold(), heat,
                "low-heat progression routes lock shut.", "#ef4444");
        addThreshold(thresholds, "Breached", policy.breachedThreshold(), heat,
                "the masquerade becomes a full breach.", "#b91c1c");
        return thresholds.stream()
                .min(java.util.Comparator.comparingDouble(ThresholdView::threshold))
                .orElseGet(() -> new ThresholdView(
                        "At full breach",
                        "Every tracked heat threshold is already active.",
                        "#b91c1c",
                        Double.MAX_VALUE));
    }

    @Nonnull
    static String compactLine(@Nonnull MasqueradeHeatSnapshot masquerade,
                              @Nonnull MasqueradeHeatPolicy policy) {
        ThresholdView nextThreshold = nextThreshold(masquerade, policy);
        return "Next threshold: " + nextThreshold.value() + " - " + nextThreshold.detail();
    }

    private static void addThreshold(@Nonnull List<ThresholdView> thresholds,
                                     @Nonnull String label,
                                     double threshold,
                                     double currentHeat,
                                     @Nonnull String consequence,
                                     @Nonnull String accentColor) {
        if (!Double.isFinite(threshold) || threshold <= currentHeat) {
            return;
        }
        if (thresholds.stream().anyMatch(existing -> Math.abs(existing.threshold() - threshold) < 0.0001d)) {
            return;
        }
        thresholds.add(new ThresholdView(
                label + " at " + formatHeat(threshold),
                formatHeat(threshold - currentHeat) + " heat remaining before " + consequence,
                accentColor,
                threshold));
    }

    @Nonnull
    private static String formatHeat(double heat) {
        return String.format(Locale.ROOT, "%.1f", Math.max(0.0d, heat));
    }

    record ThresholdView(@Nonnull String value,
                         @Nonnull String detail,
                         @Nonnull String accentColor,
                         double threshold) {
    }
}
