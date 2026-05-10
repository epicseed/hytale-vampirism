package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;

final class PressureOutlookText {

    private PressureOutlookText() {
    }

    @Nonnull
    static View resolve(@Nonnull NightHuntContinuitySnapshot continuity) {
        if (continuity.activeChainName() != null && continuity.activeChainStep() > 0) {
            return new View(
                    chainValue(continuity),
                    continuity.lastThreatEscalationReason() != null
                            ? continuity.lastThreatEscalationReason()
                            + ". Break this chain before the next hunt if you want the pressure to settle."
                            : "This chain is still active. Break pace before the next hunt if you want the pressure to settle.",
                    accentColor(continuity));
        }
        if (continuity.worldThreatLevel() > 0) {
            return new View(
                    continuity.worldThreatName() != null ? continuity.worldThreatName() : "Pressure rising",
                    continuity.lastThreatEscalationReason() != null
                            ? continuity.lastThreatEscalationReason()
                            + ". Cool off or change routes before the next hunt."
                            : "Pressure is building around your route. Cool off or change routes before the next hunt.",
                    accentColor(continuity));
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
        String chain = continuity.activeChainName() + " " + roman(continuity.activeChainStep());
        return continuity.worldThreatName() != null
                ? continuity.worldThreatName() + " · " + chain
                : chain;
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

    @Nonnull
    private static String accentColor(@Nonnull NightHuntContinuitySnapshot continuity) {
        return switch (Math.max(0, Math.min(3, continuity.worldThreatLevel()))) {
            case 1 -> "#f59e0b";
            case 2 -> "#f97316";
            case 3 -> "#ef4444";
            default -> continuity.activeChainName() != null ? "#f59e0b" : "#22c55e";
        };
    }

    record View(@Nonnull String value, @Nonnull String detail, @Nonnull String accentColor) {
    }
}
