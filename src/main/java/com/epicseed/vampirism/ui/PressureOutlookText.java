package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;
import com.epicseed.vampirism.domain.lineage.VampiricLineageEvaluation;

final class PressureOutlookText {

    private PressureOutlookText() {
    }

    @Nonnull
    static View resolve(@Nonnull NightHuntContinuitySnapshot continuity) {
        return resolve(continuity, null);
    }

    @Nonnull
    static View resolve(@Nonnull NightHuntContinuitySnapshot continuity,
                        @Nullable VampiricLineageEvaluation selectedLineage) {
        if (PressureContinuityTextSupport.hasActiveChain(continuity)) {
            return new View(
                    chainValue(continuity),
                    appendBiasDetail(
                            continuity.lastThreatEscalationReason() != null
                                    ? continuity.lastThreatEscalationReason()
                                    + ". Break this chain before the next hunt if you want the pressure to settle."
                                    : "This chain is still active. Break pace before the next hunt if you want the pressure to settle.",
                            selectedLineage),
                    PressureContinuityTextSupport.accentColor(continuity));
        }
        if (continuity.worldThreatLevel() > 0) {
            return new View(
                    continuity.worldThreatName() != null ? continuity.worldThreatName() : "Pressure rising",
                    appendBiasDetail(
                            continuity.lastThreatEscalationReason() != null
                                    ? continuity.lastThreatEscalationReason()
                                    + ". Cool off or change routes before the next hunt."
                                    : "Pressure is building around your route. Cool off or change routes before the next hunt.",
                            selectedLineage),
                    PressureContinuityTextSupport.accentColor(continuity));
        }
        if (continuity.failureStreak() > 0) {
            return new View(
                    "Trail cooling",
                    appendBiasDetail(
                            "Pressure is quiet again after recent setbacks. Reset your route before you push again.",
                            selectedLineage),
                    "#22c55e");
        }
        if (continuity.successStreak() > 1) {
            return new View(
                    "Quiet routes",
                    appendBiasDetail(
                            "Pressure is quiet, but your " + continuity.successStreak()
                                    + "-hunt streak can still snowball. Vary the next route to keep it there.",
                            selectedLineage),
                    "#22c55e");
        }
        return new View(
                "Quiet routes",
                appendBiasDetail(
                        "No active world response is building right now. Keep the next hunt varied to hold that quiet.",
                        selectedLineage),
                "#22c55e");
    }

    @Nonnull
    private static String chainValue(@Nonnull NightHuntContinuitySnapshot continuity) {
        String chain = PressureContinuityTextSupport.chainValue(continuity);
        return continuity.worldThreatName() != null
                ? continuity.worldThreatName() + " · " + chain
                : chain;
    }

    @Nonnull
    private static String appendBiasDetail(@Nonnull String detail,
                                           @Nullable VampiricLineageEvaluation selectedLineage) {
        if (selectedLineage == null) {
            return detail;
        }
        var bias = LineageAdaptationBiasText.resolve(selectedLineage);
        if (!LineageAdaptationBiasText.hasBias(bias)) {
            return detail;
        }
        return detail + " " + LineageAdaptationBiasText.futureSummary(selectedLineage, bias);
    }

    record View(@Nonnull String value, @Nonnull String detail, @Nonnull String accentColor) {
    }
}
