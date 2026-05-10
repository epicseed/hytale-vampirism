package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;

final class PressureContinuityTextSupport {

    private PressureContinuityTextSupport() {
    }

    static boolean hasActiveChain(@Nonnull NightHuntContinuitySnapshot continuity) {
        return continuity.activeChainName() != null && continuity.activeChainStep() > 0;
    }

    @Nonnull
    static String chainValue(@Nonnull NightHuntContinuitySnapshot continuity) {
        return continuity.activeChainName() + " " + roman(continuity.activeChainStep());
    }

    @Nonnull
    static String accentColor(@Nonnull NightHuntContinuitySnapshot continuity) {
        int threatLevel = Math.max(0, Math.min(3, continuity.worldThreatLevel()));
        if (threatLevel > 0) {
            return switch (threatLevel) {
                case 1 -> "#f59e0b";
                case 2 -> "#f97316";
                default -> "#ef4444";
            };
        }
        int dominantMemory = Math.max(continuity.preyMemoryLevel(), continuity.behaviorMemoryLevel());
        if (dominantMemory >= 2) {
            return "#f97316";
        }
        if (dominantMemory == 1 || continuity.successStreak() > 1 || hasActiveChain(continuity)) {
            return "#f59e0b";
        }
        return "#22c55e";
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
}
