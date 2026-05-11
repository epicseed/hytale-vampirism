package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.identity.IdentityPressureRegistry;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;

final class PressureDriversText {

    private PressureDriversText() {
    }

    @Nonnull
    static View resolve(@Nonnull NightHuntContinuitySnapshot continuity) {
        return resolve(continuity, null);
    }

    @Nonnull
    static View resolve(@Nonnull NightHuntContinuitySnapshot continuity,
                        @Nullable VampiricLineageEvaluation selectedLineage) {
        if (PressureContinuityTextSupport.hasActiveChain(continuity)) {
            return chainDriver(continuity, selectedLineage);
        }
        if (continuity.worldThreatLevel() > 0) {
            return threatDriver(continuity, selectedLineage);
        }
        String dominantMemory = dominantMemoryValue(continuity);
        if (dominantMemory != null) {
            return memoryDriver(continuity, dominantMemory, selectedLineage);
        }
        if (continuity.failureStreak() > 0) {
            return new View(
                    "Setbacks bought space",
                    appendBiasDetail(
                            "Recent failed hunts are cooling the trail. Re-enter on a different route before pressure rebuilds.",
                            continuity,
                            selectedLineage),
                    "#22c55e");
        }
        if (continuity.successStreak() > 1) {
            return new View(
                    continuity.successStreak() + "-hunt streak",
                    appendBiasDetail(
                            "Clean repeats are the only live tell right now. Change prey or approach next hunt to keep pressure quiet.",
                            continuity,
                            selectedLineage),
                    PressureContinuityTextSupport.accentColor(continuity));
        }
        return new View(
                "No active driver",
                appendBiasDetail(
                        "No chain, threat, or memory is pushing the world response right now. Keep the next hunt varied to hold that quiet.",
                        continuity,
                        selectedLineage),
                "#22c55e");
    }

    @Nonnull
    private static View chainDriver(@Nonnull NightHuntContinuitySnapshot continuity,
                                    @Nullable VampiricLineageEvaluation selectedLineage) {
        String pressure = continuity.worldThreatName() != null ? continuity.worldThreatName() : "current pressure";
        String detail = continuity.lastThreatEscalationReason() != null
                ? continuity.lastThreatEscalationReason()
                + ". This chain is keeping " + pressure + " live; break the route before the next hunt."
                : "This chain is the main pressure driver right now. Break the route before the next hunt to let it cool.";
        return new View(
                PressureContinuityTextSupport.chainValue(continuity),
                appendBiasDetail(detail, continuity, selectedLineage),
                PressureContinuityTextSupport.accentColor(continuity));
    }

    @Nonnull
    private static View threatDriver(@Nonnull NightHuntContinuitySnapshot continuity,
                                     @Nullable VampiricLineageEvaluation selectedLineage) {
        String dominantMemory = dominantMemoryValue(continuity);
        if (dominantMemory != null) {
            return new View(
                    dominantMemory,
                    appendBiasDetail(
                            dominantMemoryIsPrey(continuity)
                                    ? "That prey memory is feeding " + threatName(continuity)
                                    + ". Change prey or pace before the next hunt."
                                    : "That route memory is feeding " + threatName(continuity)
                                    + ". Change mode or resolution before the next hunt.",
                            continuity,
                            selectedLineage),
                    PressureContinuityTextSupport.accentColor(continuity));
        }
        if (continuity.successStreak() > 1) {
            return new View(
                    continuity.successStreak() + "-hunt streak",
                    appendBiasDetail(
                            threatName(continuity)
                                    + " is feeding on clean repeats. Change prey or mode before the next hunt.",
                            continuity,
                            selectedLineage),
                    PressureContinuityTextSupport.accentColor(continuity));
        }
        return new View(
                continuity.worldThreatName() != null ? continuity.worldThreatName() : "Pressure rising",
                appendBiasDetail(
                        continuity.lastThreatEscalationReason() != null
                                ? continuity.lastThreatEscalationReason() + ". Cool off or change routes before the next hunt."
                                : "The world is already reacting to your route. Cool off or change routes before the next hunt.",
                        continuity,
                        selectedLineage),
                PressureContinuityTextSupport.accentColor(continuity));
    }

    @Nonnull
    private static View memoryDriver(@Nonnull NightHuntContinuitySnapshot continuity,
                                     @Nonnull String dominantMemory,
                                     @Nullable VampiricLineageEvaluation selectedLineage) {
        return new View(
                dominantMemory,
                appendBiasDetail(
                        dominantMemoryIsPrey(continuity)
                                ? "That prey memory is the strongest live tell. Change prey or pace next hunt to keep pressure quiet."
                                : "That route memory is the strongest live tell. Change mode or resolution next hunt to keep pressure quiet.",
                        continuity,
                        selectedLineage),
                PressureContinuityTextSupport.accentColor(continuity));
    }

    @Nullable
    private static String dominantMemoryValue(@Nonnull NightHuntContinuitySnapshot continuity) {
        if (continuity.preyMemoryName() == null && continuity.behaviorMemoryName() == null) {
            return null;
        }
        if (continuity.preyMemoryLevel() >= continuity.behaviorMemoryLevel()) {
            return continuity.preyMemoryName() != null ? continuity.preyMemoryName() : continuity.behaviorMemoryName();
        }
        return continuity.behaviorMemoryName() != null ? continuity.behaviorMemoryName() : continuity.preyMemoryName();
    }

    private static boolean dominantMemoryIsPrey(@Nonnull NightHuntContinuitySnapshot continuity) {
        return continuity.preyMemoryName() != null
                && (continuity.behaviorMemoryName() == null
                || continuity.preyMemoryLevel() >= continuity.behaviorMemoryLevel());
    }

    @Nonnull
    private static String threatName(@Nonnull NightHuntContinuitySnapshot continuity) {
        return continuity.worldThreatName() != null ? continuity.worldThreatName() : "current pressure";
    }

    @Nonnull
    private static String appendBiasDetail(@Nonnull String detail,
                                           @Nonnull NightHuntContinuitySnapshot continuity,
                                           @Nullable VampiricLineageEvaluation selectedLineage) {
        String biasDetail = biasDetail(continuity, selectedLineage);
        return biasDetail != null ? detail + " " + biasDetail : detail;
    }

    @Nullable
    private static String biasDetail(@Nonnull NightHuntContinuitySnapshot continuity,
                                     @Nullable VampiricLineageEvaluation selectedLineage) {
        if (selectedLineage == null) {
            return null;
        }
        IdentityPressureRegistry.LineageAdaptationBias bias = LineageAdaptationBiasText.resolve(selectedLineage);
        if (!LineageAdaptationBiasText.hasBias(bias)) {
            return null;
        }
        if (continuity.preyMemoryLevel() > 0 || continuity.behaviorMemoryLevel() > 0) {
            return LineageAdaptationBiasText.contextSummary(selectedLineage, bias, dominantMemoryIsPrey(continuity));
        }
        return LineageAdaptationBiasText.futureSummary(selectedLineage, bias);
    }

    record View(@Nonnull String value, @Nonnull String detail, @Nonnull String accentColor) {
    }
}
