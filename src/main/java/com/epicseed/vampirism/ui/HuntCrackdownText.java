package com.epicseed.vampirism.ui;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.vampirism.domain.player.NamedHuntProgress;
import com.epicseed.epiccore.vampirism.domain.player.PersistedNightHuntState;
import com.epicseed.vampirism.domain.hunt.NightHuntCasefileService;
import com.epicseed.vampirism.domain.hunt.NightHuntContinuitySnapshot;

final class HuntCrackdownText {

    private HuntCrackdownText() {
    }

    @Nonnull
    static View resolve(@Nonnull PersistedNightHuntState persisted,
                        @Nonnull NightHuntContinuitySnapshot continuity,
                        @Nonnull NamedHuntProgress progress) {
        return resolve(persisted, continuity, progress, System.currentTimeMillis());
    }

    @Nonnull
    static View resolve(@Nonnull PersistedNightHuntState persisted,
                        @Nonnull NightHuntContinuitySnapshot continuity,
                        @Nonnull NamedHuntProgress progress,
                        long nowMs) {
        float cooldownRemainingSeconds = persisted.cooldownRemainingMs(nowMs) / 1000f;
        HuntCasefileText.View casefile = HuntCasefileText.resolve(persisted, progress);
        String lastClearedCasefile = NightHuntCasefileService.lastClearedCasefileDisplayName(progress);
        if (persisted.crackdownTier > 0 && persisted.crackdownExtraCooldownSeconds > 0f && cooldownRemainingSeconds > 0f) {
            String label = label(persisted, continuity);
            String detail = "Next hunt window delayed +" + formatSeconds(persisted.crackdownExtraCooldownSeconds)
                    + "s; routes reopen in " + formatSeconds(cooldownRemainingSeconds) + "s.";
            if (continuity.lastThreatEscalationReason() != null) {
                detail = detail + " " + continuity.lastThreatEscalationReason() + ".";
            } else if (continuity.activeChainName() != null && continuity.activeChainStep() > 0) {
                detail = detail + " Break the active chain before the next hunt if you want the pressure to ease.";
            } else {
                detail = detail + " Cool the trail before the next hunt if you want the pressure to ease.";
            }
            if (casefile.active()) {
                detail = detail + " " + casefile.value() + " remains open. " + casefile.detail();
            } else if (lastClearedCasefile != null) {
                detail = detail + " The last cleared file was " + lastClearedCasefile
                        + ", so the hunters are pivoting before they reopen the same route.";
            }
            return new View(label, detail, accentColor(persisted.crackdownTier), true);
        }
        if (cooldownRemainingSeconds > 0f) {
            String detail = "No hunter crackdown is adding extra downtime right now. The next hunt window opens in "
                    + formatSeconds(cooldownRemainingSeconds) + "s.";
            if (casefile.active()) {
                detail = detail + " " + casefile.value() + " is still steering the next hunt.";
            } else if (lastClearedCasefile != null) {
                detail = detail + " The last cleared file was " + lastClearedCasefile
                        + ", so the next response is likely to pivot instead of repeating it immediately.";
            }
            return new View(
                    "Cooldown settling",
                    detail,
                    continuity.worldThreatLevel() > 0 ? PressureContinuityTextSupport.accentColor(continuity) : "#22c55e",
                    false);
        }
        return new View(
                "Routes open",
                casefile.active()
                        ? "No active crackdown is delaying the next hunt window, but " + casefile.value()
                        + " still biases the trail."
                        : lastClearedCasefile != null
                        ? "No active crackdown is delaying the next hunt window. The last cleared file was "
                        + lastClearedCasefile + ", so the hunters are pivoting away from an immediate repeat."
                        : "No active crackdown is delaying the next hunt window.",
                "#22c55e",
                false);
    }

    @Nonnull
    private static String label(@Nonnull PersistedNightHuntState persisted,
                                @Nonnull NightHuntContinuitySnapshot continuity) {
        String threatName = continuity.worldThreatName() != null
                ? continuity.worldThreatName()
                : persisted.worldThreatName != null ? persisted.worldThreatName : fallbackThreatName(persisted.crackdownTier);
        if (continuity.activeChainName() != null && continuity.activeChainStep() > 0) {
            return threatName + " · " + continuity.activeChainName() + " " + roman(continuity.activeChainStep());
        }
        return threatName;
    }

    @Nonnull
    private static String fallbackThreatName(int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 1 -> "Hunter watch";
            case 2 -> "Hunter crackdown";
            case 3 -> "Crimson dragnet";
            default -> "Routes open";
        };
    }

    @Nonnull
    private static String accentColor(int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 1 -> "#f59e0b";
            case 2 -> "#f97316";
            case 3 -> "#ef4444";
            default -> "#22c55e";
        };
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
    private static String formatSeconds(float seconds) {
        return Integer.toString((int) Math.ceil(Math.max(0f, seconds)));
    }

    record View(@Nonnull String value, @Nonnull String detail, @Nonnull String accentColor, boolean active) {
    }
}
