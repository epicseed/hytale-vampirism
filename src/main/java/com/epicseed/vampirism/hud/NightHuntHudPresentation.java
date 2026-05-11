package com.epicseed.vampirism.hud;

import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.vampirism.domain.hunt.NightHuntStatusSnapshot;
import com.epicseed.vampirism.domain.hunt.NightHuntPreparedLoadout;
import com.epicseed.vampirism.domain.hunt.NightHuntPresentationText;

final class NightHuntHudPresentation {
    private static final String TITLE = "Night Hunt";
    private static final Palette CALM_PALETTE = new Palette(
            "#34161f(0.94)",
            "#f6dce3",
            "#f7d2d8",
            "#f7d2d8",
            "#f0e2e6",
            "#dcb6c0");
    private static final Palette WARNING_PALETTE = new Palette(
            "#5c3810(0.96)",
            "#ffefc7",
            "#fff3d6",
            "#ffd27a",
            "#f7dfb1",
            "#e3c79b");
    private static final Palette DANGER_PALETTE = new Palette(
            "#5b1621(0.96)",
            "#ffe0e5",
            "#fff0f2",
            "#ff9aa6",
            "#ffc8d0",
            "#f3a9b5");

    private NightHuntHudPresentation() {
    }

    @Nonnull
    static DisplayState present(@Nullable NightHuntStatusSnapshot snapshot) {
        if (snapshot == null || !snapshot.active() || "approaching".equals(snapshot.phase())) {
            return DisplayState.hidden();
        }
        NightHuntPreparedLoadout loadout = NightHuntPresentationText.loadout(
                snapshot.selectedPreparationId(),
                snapshot.activeModeId());
        Palette palette = palette(snapshot);
        return new DisplayState(
                true,
                TITLE,
                header(snapshot, loadout),
                phaseLabel(snapshot, loadout),
                progress(snapshot, loadout),
                guidance(snapshot, loadout),
                detail(snapshot, loadout),
                secondary(snapshot, loadout),
                palette);
    }

    @Nonnull
    private static String phaseLabel(@Nonnull NightHuntStatusSnapshot snapshot,
                                     @Nonnull NightHuntPreparedLoadout loadout) {
        return switch (snapshot.phase()) {
            case "approaching" -> "Omen";
            case "guiding", "route-pending" -> "Trail";
            case "summoning" -> "Manifest";
            case "prey-active" -> switch (loadout.resolutionId().toLowerCase(Locale.ROOT)) {
                case "drain" -> "Drain";
                case "scare-off" -> "Dread";
                default -> "Contract";
            };
            default -> formatPhase(snapshot.phase());
        };
    }

    @Nonnull
    private static String header(@Nonnull NightHuntStatusSnapshot snapshot,
                                 @Nonnull NightHuntPreparedLoadout loadout) {
        return switch (snapshot.phase()) {
            case "guiding" -> "Reach waypoint "
                    + Math.min(Math.max(1, snapshot.currentStep()), Math.max(1, snapshot.totalWaypoints()))
                    + " of " + Math.max(1, snapshot.totalWaypoints());
            case "route-pending" -> "Hold while the trail reroutes";
            case "summoning" -> "Hold the omen";
            case "prey-active" -> preyActiveHeader(snapshot, loadout);
            default -> TITLE;
        };
    }

    @Nonnull
    private static String progress(@Nonnull NightHuntStatusSnapshot snapshot,
                                   @Nonnull NightHuntPreparedLoadout loadout) {
        if ("route-pending".equals(snapshot.phase())) {
            return "Repathing";
        }
        if ("summoning".equals(snapshot.phase())) {
            return "Prey incoming";
        }
        if (snapshot.preyActive()) {
            if (snapshot.objectiveTarget() <= 0 && snapshot.pressureTargetSeconds() <= 0f) {
                return "Live";
            }
            if (holdingStage(snapshot)) {
                return Math.max(0, Math.round(snapshot.pressureProgressSeconds()))
                        + " / " + Math.max(1, Math.round(snapshot.pressureTargetSeconds())) + "s";
            }
            return boundedProgress(snapshot) + " / " + snapshot.objectiveTarget();
        }
        int total = Math.max(1, snapshot.totalWaypoints());
        int current = Math.max(1, snapshot.currentStep());
        return Math.min(current, total) + " / " + total;
    }

    @Nonnull
    private static String guidance(@Nonnull NightHuntStatusSnapshot snapshot,
                                   @Nonnull NightHuntPreparedLoadout loadout) {
        return switch (snapshot.phase()) {
            case "guiding" -> "Follow the blood trail to the next waypoint.";
            case "route-pending" -> "Stay ready while the next route resolves.";
            case "summoning" -> "Stay on the omen until the prey manifests.";
            case "prey-active" -> preyActiveGuidance(snapshot, loadout);
            default -> "The hunt is stirring.";
        };
    }

    @Nonnull
    private static String detail(@Nonnull NightHuntStatusSnapshot snapshot,
                                 @Nonnull NightHuntPreparedLoadout loadout) {
        if (!snapshot.preyActive()) {
            return "Loadout \u00b7 " + loadout.preparationDisplayName() + " \u00b7 " + loadout.modeDisplayName();
        }
        if (snapshot.preyLifetimeRemainingSeconds() > 0f) {
            return (urgency(snapshot) == Urgency.DANGER ? "Risk" : "Timer")
                    + " \u00b7 scent "
                    + Math.max(1, Math.round(snapshot.preyLifetimeRemainingSeconds()))
                    + "s left";
        }
        if (holdingStage(snapshot)) {
            return "Status \u00b7 stay within " + trimRadius(loadout.pressureRadius()) + "m";
        }
        int remaining = Math.max(0, snapshot.objectiveTarget() - boundedProgress(snapshot));
        if (remaining > 0) {
            return "Status \u00b7 " + remainingObjectiveText(remaining, loadout);
        }
        return "Status \u00b7 contract live";
    }

    @Nonnull
    private static String secondary(@Nonnull NightHuntStatusSnapshot snapshot,
                                    @Nonnull NightHuntPreparedLoadout loadout) {
        if (snapshot.preyActive()) {
            StringBuilder builder = new StringBuilder("Target \u00b7 ");
            builder.append(targetName(snapshot));
            appendSegment(builder, snapshot.riskName());
            appendSegment(builder, snapshot.setpieceName());
            appendSegment(builder, signatureLabel(snapshot));
            appendSegment(builder, casefileLabel(snapshot));
            appendSegment(builder, resonanceLabel(snapshot));
            if (builder.toString().equals("Target \u00b7 " + targetName(snapshot))
                    && holdingStage(snapshot)) {
                appendSegment(builder, "Radius " + trimRadius(loadout.pressureRadius()) + "m");
            }
            return builder.toString();
        }
        StringBuilder builder = new StringBuilder("Conditions");
        if (snapshot.environmentName() != null && !snapshot.environmentName().isBlank()) {
            appendSegment(builder, snapshot.environmentName());
        }
        appendSegment(builder, snapshot.riskName());
        appendSegment(builder, snapshot.setpieceName());
        appendSegment(builder, signatureLabel(snapshot));
        appendSegment(builder, casefileLabel(snapshot));
        appendSegment(builder, resonanceLabel(snapshot));
        if ("Conditions".equals(builder.toString())) {
            appendSegment(builder, "Trail tier " + Math.max(1, snapshot.visualTier()));
        }
        return builder.toString();
    }

    @Nullable
    private static String resonanceLabel(@Nonnull NightHuntStatusSnapshot snapshot) {
        if (snapshot.resonancePreyFamilyId() == null || snapshot.resonancePreyFamilyId().isBlank()) {
            return null;
        }
        return "Resonance " + NightHuntPresentationText.humanize(snapshot.resonancePreyFamilyId());
    }

    @Nullable
    private static String signatureLabel(@Nonnull NightHuntStatusSnapshot snapshot) {
        if (snapshot.signaturePreyFamilyId() == null || snapshot.signaturePreyFamilyId().isBlank()) {
            return null;
        }
        return "Signature " + NightHuntPresentationText.humanize(snapshot.signaturePreyFamilyId());
    }

    @Nullable
    private static String casefileLabel(@Nonnull NightHuntStatusSnapshot snapshot) {
        if (snapshot.casefileName() == null || snapshot.casefileName().isBlank()) {
            return null;
        }
        return "escalated".equalsIgnoreCase(snapshot.casefileStage())
                ? "Casefile " + snapshot.casefileName() + " (Escalated)"
                : "Casefile " + snapshot.casefileName();
    }

    @Nonnull
    private static String targetName(@Nonnull NightHuntStatusSnapshot snapshot) {
        return snapshot.preyName() == null || snapshot.preyName().isBlank()
                ? "the marked prey"
                : snapshot.preyName();
    }

    @Nonnull
    private static String formatPhase(@Nonnull String phase) {
        if (phase.isBlank()) {
            return "";
        }
        String normalized = phase.replace('-', ' ').toLowerCase(Locale.ROOT);
        String[] parts = normalized.split(" ");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }

    @Nonnull
    private static String preyActiveGuidance(@Nonnull NightHuntStatusSnapshot snapshot,
                                             @Nonnull NightHuntPreparedLoadout loadout) {
        String resolutionId = loadout.resolutionId().trim().toLowerCase(Locale.ROOT);
        return switch (resolutionId) {
            case "drain" -> holdingStage(snapshot)
                    ? "Stay within " + trimRadius(loadout.pressureRadius()) + "m until the drain locks."
                    : "Prime the siphon before the prey slips away.";
            case "scare-off" -> holdingStage(snapshot)
                    ? "Keep pressure on the prey until it flees."
                    : "Break the prey's nerve until the flee window opens.";
            default -> "Finish the marked prey before the scent dies.";
        };
    }

    @Nonnull
    private static String preyActiveHeader(@Nonnull NightHuntStatusSnapshot snapshot,
                                           @Nonnull NightHuntPreparedLoadout loadout) {
        String resolutionId = loadout.resolutionId().trim().toLowerCase(Locale.ROOT);
        if (holdingStage(snapshot)) {
            return switch (resolutionId) {
                case "drain" -> "Hold the drain";
                case "scare-off" -> "Force the prey to flee";
                default -> "Finish the target";
            };
        }
        return switch (resolutionId) {
            case "drain" -> "Prime the siphon";
            case "scare-off" -> "Break the prey's nerve";
            default -> "Kill the marked prey";
        };
    }

    private static boolean holdingStage(@Nonnull NightHuntStatusSnapshot snapshot) {
        return snapshot.pressureTargetSeconds() > 0f
                && snapshot.objectiveProgress() >= snapshot.objectiveTarget();
    }

    @Nonnull
    private static String remainingObjectiveText(int remaining,
                                                 @Nonnull NightHuntPreparedLoadout loadout) {
        String resolutionId = loadout.resolutionId().trim().toLowerCase(Locale.ROOT);
        return switch (resolutionId) {
            case "drain" -> remaining + " more to prime";
            case "scare-off" -> remaining + " more to break";
            default -> remaining + " more to claim";
        };
    }

    @Nonnull
    private static Palette palette(@Nonnull NightHuntStatusSnapshot snapshot) {
        return switch (urgency(snapshot)) {
            case DANGER -> DANGER_PALETTE;
            case WARNING -> WARNING_PALETTE;
            default -> CALM_PALETTE;
        };
    }

    @Nonnull
    private static Urgency urgency(@Nonnull NightHuntStatusSnapshot snapshot) {
        if (snapshot.preyActive() && snapshot.preyLifetimeRemainingSeconds() > 0f) {
            if (snapshot.preyLifetimeRemainingSeconds() <= 15f) {
                return Urgency.DANGER;
            }
            if (snapshot.preyLifetimeRemainingSeconds() <= 30f) {
                return Urgency.WARNING;
            }
        }
        if ("route-pending".equals(snapshot.phase()) || "summoning".equals(snapshot.phase())) {
            return Urgency.WARNING;
        }
        return Urgency.CALM;
    }

    private static int boundedProgress(@Nonnull NightHuntStatusSnapshot snapshot) {
        if (snapshot.objectiveTarget() <= 0) {
            return 0;
        }
        return Math.min(Math.max(0, snapshot.objectiveProgress()), snapshot.objectiveTarget());
    }

    private static void appendSegment(@Nonnull StringBuilder builder, @Nullable String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(" · ");
        }
        builder.append(value);
    }

    @Nonnull
    private static String trimRadius(double value) {
        if (Math.abs(value - Math.round(value)) < 0.01d) {
            return Integer.toString((int) Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private enum Urgency {
        CALM,
        WARNING,
        DANGER
    }

    record Palette(@Nonnull String chipBackground,
                   @Nonnull String phaseText,
                   @Nonnull String headerText,
                   @Nonnull String progressText,
                   @Nonnull String guidanceText,
                   @Nonnull String contextText) {
    }

    record DisplayState(boolean visible,
                        @Nonnull String title,
                        @Nonnull String header,
                        @Nonnull String phase,
                        @Nonnull String progress,
                        @Nonnull String guidance,
                        @Nonnull String context,
                        @Nonnull String target,
                        @Nonnull Palette palette) {

        @Nonnull
        static DisplayState hidden() {
            return new DisplayState(false, "", "", "", "", "", "", "", CALM_PALETTE);
        }
    }
}
