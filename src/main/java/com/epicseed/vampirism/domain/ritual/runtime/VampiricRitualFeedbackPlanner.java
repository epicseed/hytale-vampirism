package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;

final class VampiricRitualFeedbackPlanner {

    static final long TRACE_CADENCE_INTERVAL_MS = 180L;
    static final long CHANNEL_CADENCE_INTERVAL_MS = 950L;
    static final long UNSTABLE_CADENCE_INTERVAL_MS = 650L;

    private VampiricRitualFeedbackPlanner() {
    }

    @Nonnull
    static FeedbackPlan plan(@Nullable RitualFeedbackState previous,
                             @Nullable VampiricRitualRuntimeSnapshot snapshot,
                             long nowMs) {
        List<RitualCue> cues = new ArrayList<>();
        if (snapshot == null) {
            if (previous != null
                    && previous.phase() != VampiricRitualRuntimePhase.SUCCESS
                    && previous.phase() != VampiricRitualRuntimePhase.COLLAPSE) {
                cues.add(RitualCue.RITUAL_CLEARED);
            }
            return new FeedbackPlan(cues, null);
        }

        String tracingPointId = tracingPointId(snapshot);
        int traceStrokeCount = traceStrokeCount(snapshot, tracingPointId);
        long nextTraceCadenceAtMs = previous != null && Objects.equals(previous.tracingPointId(), tracingPointId)
                ? previous.nextTraceCadenceAtMs()
                : nowMs + TRACE_CADENCE_INTERVAL_MS;
        long nextChannelCadenceAtMs = previous != null && previous.phase() == snapshot.phase()
                ? previous.nextChannelCadenceAtMs()
                : nowMs + channelCadenceInterval(snapshot.phase());

        if (previous == null) {
            if (tracingPointId != null) {
                cues.add(RitualCue.TRACE_STARTED);
            } else if (snapshot.activatedPoints() > 0) {
                cues.add(RitualCue.GLYPH_SEALED);
            }
            RitualCue initialPhaseCue = phaseCue(null, snapshot.phase());
            if (initialPhaseCue != null) {
                cues.add(initialPhaseCue);
            }
            if (snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE
                    && nowMs >= nextChannelCadenceAtMs) {
                cues.add(RitualCue.UNSTABLE_CADENCE);
                nextChannelCadenceAtMs = nowMs + UNSTABLE_CADENCE_INTERVAL_MS;
            } else if (snapshot.phase() == VampiricRitualRuntimePhase.CHANNELING
                    && nowMs >= nextChannelCadenceAtMs) {
                cues.add(RitualCue.CHANNEL_CADENCE);
                nextChannelCadenceAtMs = nowMs + CHANNEL_CADENCE_INTERVAL_MS;
            }
            return new FeedbackPlan(
                    cues,
                    new RitualFeedbackState(
                            snapshot.phase(),
                            snapshot.activatedPoints(),
                            tracingPointId,
                            traceStrokeCount,
                            snapshot.interferenceCount(),
                            nextTraceCadenceAtMs,
                            nextChannelCadenceAtMs));
        }

        if (previous.activatedPoints() < snapshot.activatedPoints()) {
            cues.add(RitualCue.GLYPH_SEALED);
        } else if (previous.activatedPoints() > snapshot.activatedPoints()) {
            cues.add(RitualCue.GLYPH_UNSEALED);
        }

        if (previous.tracingPointId() == null && tracingPointId != null) {
            cues.add(RitualCue.TRACE_STARTED);
            nextTraceCadenceAtMs = nowMs + TRACE_CADENCE_INTERVAL_MS;
        } else if (previous.tracingPointId() != null
                && tracingPointId == null
                && previous.activatedPoints() == snapshot.activatedPoints()) {
            cues.add(RitualCue.TRACE_REJECTED);
        } else if (tracingPointId != null
                && Objects.equals(previous.tracingPointId(), tracingPointId)
                && traceStrokeCount > previous.traceStrokeCount()
                && nowMs >= previous.nextTraceCadenceAtMs()) {
            cues.add(RitualCue.TRACE_CADENCE);
            nextTraceCadenceAtMs = nowMs + TRACE_CADENCE_INTERVAL_MS;
        }

        if (snapshot.interferenceCount() > previous.interferenceCount()) {
            cues.add(RitualCue.INTERFERENCE_SPIKE);
        }

        RitualCue phaseCue = phaseCue(previous.phase(), snapshot.phase());
        if (phaseCue != null) {
            cues.add(phaseCue);
            nextChannelCadenceAtMs = nowMs + channelCadenceInterval(snapshot.phase());
        }

        if (snapshot.phase() == VampiricRitualRuntimePhase.UNSTABLE
                && nowMs >= nextChannelCadenceAtMs) {
            cues.add(RitualCue.UNSTABLE_CADENCE);
            nextChannelCadenceAtMs = nowMs + UNSTABLE_CADENCE_INTERVAL_MS;
        } else if (snapshot.phase() == VampiricRitualRuntimePhase.CHANNELING
                && nowMs >= nextChannelCadenceAtMs) {
            cues.add(RitualCue.CHANNEL_CADENCE);
            nextChannelCadenceAtMs = nowMs + CHANNEL_CADENCE_INTERVAL_MS;
        }

        return new FeedbackPlan(
                cues,
                new RitualFeedbackState(
                        snapshot.phase(),
                        snapshot.activatedPoints(),
                        tracingPointId,
                        traceStrokeCount,
                        snapshot.interferenceCount(),
                        nextTraceCadenceAtMs,
                        nextChannelCadenceAtMs));
    }

    @Nullable
    private static RitualCue phaseCue(@Nullable VampiricRitualRuntimePhase previousPhase,
                                      @Nonnull VampiricRitualRuntimePhase currentPhase) {
        if (previousPhase == currentPhase) {
            return null;
        }
        if (previousPhase == VampiricRitualRuntimePhase.UNSTABLE
                && currentPhase == VampiricRitualRuntimePhase.CHANNELING) {
            return RitualCue.PHASE_STEADIED;
        }
        return switch (currentPhase) {
            case PREPARING -> null;
            case BINDING -> RitualCue.PHASE_BINDING;
            case CHANNELING -> RitualCue.PHASE_CHANNELING;
            case UNSTABLE -> RitualCue.PHASE_UNSTABLE;
            case SUCCESS -> RitualCue.PHASE_SUCCESS;
            case COLLAPSE -> RitualCue.PHASE_COLLAPSE;
        };
    }

    private static long channelCadenceInterval(@Nonnull VampiricRitualRuntimePhase phase) {
        return switch (phase) {
            case UNSTABLE -> UNSTABLE_CADENCE_INTERVAL_MS;
            case CHANNELING -> CHANNEL_CADENCE_INTERVAL_MS;
            default -> Long.MAX_VALUE / 4L;
        };
    }

    @Nullable
    private static String tracingPointId(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        for (VampiricRitualPointState point : snapshot.pointStates()) {
            if (point.tracing() && !point.active()) {
                return point.pointId();
            }
        }
        return null;
    }

    private static int traceStrokeCount(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                        @Nullable String pointId) {
        if (pointId == null) {
            return 0;
        }
        for (VampiricRitualPointState point : snapshot.pointStates()) {
            if (point.pointId().equals(pointId)) {
                return point.traceStrokePositions().size();
            }
        }
        return 0;
    }

    enum RitualCue {
        TRACE_STARTED,
        TRACE_REJECTED,
        TRACE_CADENCE,
        GLYPH_SEALED,
        GLYPH_UNSEALED,
        INTERFERENCE_SPIKE,
        PHASE_BINDING,
        PHASE_CHANNELING,
        PHASE_UNSTABLE,
        PHASE_STEADIED,
        CHANNEL_CADENCE,
        UNSTABLE_CADENCE,
        PHASE_SUCCESS,
        PHASE_COLLAPSE,
        RITUAL_CLEARED
    }

    record FeedbackPlan(
            @Nonnull List<RitualCue> cues,
            @Nullable RitualFeedbackState nextState) {

        FeedbackPlan {
            Objects.requireNonNull(cues, "cues");
            cues = List.copyOf(cues);
        }
    }

    record RitualFeedbackState(
            @Nonnull VampiricRitualRuntimePhase phase,
            int activatedPoints,
            @Nullable String tracingPointId,
            int traceStrokeCount,
            int interferenceCount,
            long nextTraceCadenceAtMs,
            long nextChannelCadenceAtMs) {

        RitualFeedbackState {
            Objects.requireNonNull(phase, "phase");
        }
    }
}
