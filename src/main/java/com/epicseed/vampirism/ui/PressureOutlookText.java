package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;

final class PressureOutlookText {

    private PressureOutlookText() {
    }

    @Nonnull
    static View resolve(@Nonnull NightHuntContinuitySnapshot continuity) {
        if (PressureContinuityTextSupport.hasActiveChain(continuity)) {
            return new View(
                    chainValue(continuity),
                    continuity.lastThreatEscalationReason() != null
                            ? continuity.lastThreatEscalationReason()
                            + ". Break this chain before the next hunt if you want the pressure to settle."
                            : "This chain is still active. Break pace before the next hunt if you want the pressure to settle.",
                    PressureContinuityTextSupport.accentColor(continuity));
        }
        if (continuity.worldThreatLevel() > 0) {
            return new View(
                    continuity.worldThreatName() != null ? continuity.worldThreatName() : "Pressure rising",
                    continuity.lastThreatEscalationReason() != null
                            ? continuity.lastThreatEscalationReason()
                            + ". Cool off or change routes before the next hunt."
                            : "Pressure is building around your route. Cool off or change routes before the next hunt.",
                    PressureContinuityTextSupport.accentColor(continuity));
        }
        if (continuity.failureStreak() > 0) {
            return new View(
                    "Trail cooling",
                    "Pressure is quiet again after recent setbacks. Reset your route before you push again.",
                    "#22c55e");
        }
        if (continuity.successStreak() > 1) {
            return new View(
                    "Quiet routes",
                    "Pressure is quiet, but your " + continuity.successStreak()
                            + "-hunt streak can still snowball. Vary the next route to keep it there.",
                    "#22c55e");
        }
        return new View(
                "Quiet routes",
                "No active world response is building right now. Keep the next hunt varied to hold that quiet.",
                "#22c55e");
    }

    @Nonnull
    private static String chainValue(@Nonnull NightHuntContinuitySnapshot continuity) {
        String chain = PressureContinuityTextSupport.chainValue(continuity);
        return continuity.worldThreatName() != null
                ? continuity.worldThreatName() + " · " + chain
                : chain;
    }

    record View(@Nonnull String value, @Nonnull String detail, @Nonnull String accentColor) {
    }
}
