package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.identity.IdentityPressureRegistry;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;

final class LineageAdaptationBiasText {

    private static final double EPSILON = 0.0001d;

    private LineageAdaptationBiasText() {
    }

    @Nonnull
    static IdentityPressureRegistry.LineageAdaptationBias resolve(@Nullable VampiricLineageEvaluation selectedLineage) {
        return selectedLineage != null
                ? IdentityPressureRegistry.get().lineageAdaptationBias(selectedLineage.definition().id())
                : IdentityPressureRegistry.LineageAdaptationBias.baseline();
    }

    static boolean hasBias(@Nonnull IdentityPressureRegistry.LineageAdaptationBias bias) {
        return !bias.isBaseline();
    }

    static boolean preyDominant(@Nonnull IdentityPressureRegistry.LineageAdaptationBias bias) {
        return bias.preyMemoryMultiplier() > bias.behaviorMemoryMultiplier() + EPSILON;
    }

    static boolean behaviorDominant(@Nonnull IdentityPressureRegistry.LineageAdaptationBias bias) {
        return bias.behaviorMemoryMultiplier() > bias.preyMemoryMultiplier() + EPSILON;
    }

    @Nonnull
    static String generalSummary(@Nonnull VampiricLineageEvaluation selectedLineage,
                                 @Nonnull IdentityPressureRegistry.LineageAdaptationBias bias) {
        String displayName = selectedLineage.definition().displayName();
        if (!hasBias(bias)) {
            return displayName + " keeps prey memory and route counterplay balanced.";
        }
        if (preyDominant(bias)) {
            return displayName + " bends live adaptation toward prey memory ("
                    + signedPercent(bias.preyMemoryMultiplier()) + " prey adaptation, "
                    + signedPercent(bias.behaviorMemoryMultiplier()) + " route counterplay).";
        }
        if (behaviorDominant(bias)) {
            return displayName + " bends live adaptation toward route counterplay ("
                    + signedPercent(bias.behaviorMemoryMultiplier()) + " route counterplay, "
                    + signedPercent(bias.preyMemoryMultiplier()) + " prey adaptation).";
        }
        return displayName + " lands live adaptation " + signedPercent(bias.preyMemoryMultiplier())
                + " across prey memory and route counterplay.";
    }

    @Nonnull
    static String contextSummary(@Nonnull VampiricLineageEvaluation selectedLineage,
                                 @Nonnull IdentityPressureRegistry.LineageAdaptationBias bias,
                                 boolean preyContext) {
        String displayName = selectedLineage.definition().displayName();
        String focus = preyContext ? "prey memory" : "route counterplay";
        double multiplier = preyContext ? bias.preyMemoryMultiplier() : bias.behaviorMemoryMultiplier();
        if (Math.abs(multiplier - 1.0d) < EPSILON) {
            return displayName + " leaves that " + focus + " at baseline.";
        }
        if (multiplier > 1.0d) {
            return displayName + " makes that " + focus + " land " + signedPercent(multiplier) + " harder.";
        }
        return displayName + " softens that " + focus + " by " + percentDelta(multiplier) + ".";
    }

    @Nonnull
    static String futureSummary(@Nonnull VampiricLineageEvaluation selectedLineage,
                                @Nonnull IdentityPressureRegistry.LineageAdaptationBias bias) {
        String displayName = selectedLineage.definition().displayName();
        if (!hasBias(bias)) {
            return displayName + " keeps the next live adaptation balanced.";
        }
        if (preyDominant(bias)) {
            return displayName + " will bend the next live adaptation toward prey memory.";
        }
        if (behaviorDominant(bias)) {
            return displayName + " will bend the next live adaptation toward route counterplay.";
        }
        return displayName + " will keep the next live adaptation even across prey memory and route counterplay.";
    }

    @Nonnull
    static String signedPercent(double multiplier) {
        int delta = (int) Math.round((multiplier - 1.0d) * 100.0d);
        return (delta >= 0 ? "+" : "") + delta + "%";
    }

    @Nonnull
    private static String percentDelta(double multiplier) {
        return Math.abs((int) Math.round((multiplier - 1.0d) * 100.0d)) + "%";
    }
}
