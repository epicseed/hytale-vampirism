package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;

public final class VampiricRitualOutcomeTracker {

    private static final long OUTCOME_TTL_MS = 45_000L;

    private static final Map<UUID, RitualOutcome> RECENT_OUTCOMES = new ConcurrentHashMap<>();

    private VampiricRitualOutcomeTracker() {
    }

    public static void recordBacklash(@Nullable UUID uuid,
                                      @Nullable String ritualId,
                                      int bloodLoss,
                                      @Nonnull VampiricRitualRuntimePhase phase,
                                      int interferenceCount) {
        if (uuid == null || ritualId == null || ritualId.isBlank()) {
            return;
        }
        RECENT_OUTCOMES.put(uuid, new RitualOutcome(
                RitualOutcomeType.BACKLASH,
                ritualId.trim(),
                Math.max(0, bloodLoss),
                Math.max(0, interferenceCount),
                0d,
                phase,
                System.currentTimeMillis()));
    }

    public static void recordCollapse(@Nullable UUID uuid,
                                      @Nullable String ritualId,
                                      int bloodLoss,
                                      int interferenceCount,
                                      double corruption) {
        if (uuid == null || ritualId == null || ritualId.isBlank()) {
            return;
        }
        RECENT_OUTCOMES.put(uuid, new RitualOutcome(
                RitualOutcomeType.COLLAPSE,
                ritualId.trim(),
                Math.max(0, bloodLoss),
                Math.max(0, interferenceCount),
                Math.max(0d, corruption),
                VampiricRitualRuntimePhase.COLLAPSE,
                System.currentTimeMillis()));
    }

    @Nonnull
    public static Optional<RitualOutcome> recentOutcome(@Nullable UUID uuid) {
        return recentOutcome(uuid, System.currentTimeMillis());
    }

    @Nonnull
    static Optional<RitualOutcome> recentOutcome(@Nullable UUID uuid, long nowMs) {
        if (uuid == null) {
            return Optional.empty();
        }
        RitualOutcome outcome = RECENT_OUTCOMES.get(uuid);
        if (outcome == null) {
            return Optional.empty();
        }
        if (nowMs - outcome.occurredAtMs() > OUTCOME_TTL_MS) {
            RECENT_OUTCOMES.remove(uuid, outcome);
            return Optional.empty();
        }
        return Optional.of(outcome);
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        if (uuid != null) {
            RECENT_OUTCOMES.remove(uuid);
        }
    }

    @Nonnull
    public static String describeAnchorState(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        VampiricRitualAnchorState state = VampiricRitualAnchorState.fromSnapshot(snapshot);
        return "anchor=" + state.displayName() + " | hint=" + anchorHint(snapshot);
    }

    @Nonnull
    public static String anchorHint(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        return switch (VampiricRitualAnchorState.fromSnapshot(snapshot)) {
            case PREPARED -> snapshot.complete()
                    ? "Press Ability3 beside the coffin to commit the completed circle."
                    : "Trace the remaining sigils around the coffin.";
            case BINDING -> "Stay beside the coffin while the committed circle takes hold.";
            case ACTIVE -> "Stay beside the coffin until the ritual settles.";
            case UNSTABLE -> "Recover stability quickly or the circle will collapse.";
            case COLLAPSE -> "The circle collapsed; retrace the sigils before trying again.";
            case COMPLETE -> "The ritual has settled. Its afterimage will fade on its own shortly.";
        };
    }

    @Nonnull
    public static String describeOutcome(@Nonnull RitualOutcome outcome) {
        if (outcome.type() == RitualOutcomeType.COLLAPSE) {
            return "Recent outcome: collapse drained " + outcome.bloodLoss()
                    + " blood | interference=" + outcome.interferenceCount()
                    + " | corruption=" + Math.round(outcome.corruption()) + "%";
        }
        return "Recent outcome: backlash drained " + outcome.bloodLoss()
                + " blood | phase=" + formatPhase(outcome.phase())
                + " | interference=" + outcome.interferenceCount();
    }

    @Nonnull
    private static String formatPhase(@Nonnull VampiricRitualRuntimePhase phase) {
        String name = phase.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    public enum RitualOutcomeType {
        BACKLASH,
        COLLAPSE
    }

    public record RitualOutcome(
            @Nonnull RitualOutcomeType type,
            @Nonnull String ritualId,
            int bloodLoss,
            int interferenceCount,
            double corruption,
            @Nonnull VampiricRitualRuntimePhase phase,
            long occurredAtMs) {
    }
}
