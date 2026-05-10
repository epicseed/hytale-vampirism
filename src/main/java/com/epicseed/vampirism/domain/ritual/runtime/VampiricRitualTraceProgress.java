package com.epicseed.vampirism.domain.ritual.runtime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;

public final class VampiricRitualTraceProgress {

    private VampiricRitualTraceProgress() {
    }

    public static int displayCurrentStep(int traceProgress, int totalTraceSteps) {
        if (totalTraceSteps <= 0) {
            return 0;
        }
        return Math.max(1, Math.min(totalTraceSteps, traceProgress + 1));
    }

    @Nonnull
    public static String displayProgressText(int traceProgress, int totalTraceSteps) {
        int total = Math.max(0, totalTraceSteps);
        if (total <= 0) {
            return "0/0";
        }
        return displayCurrentStep(traceProgress, totalTraceSteps) + "/" + total;
    }

    @Nonnull
    public static String displayProgressText(@Nullable VampiricRitualPointState pointState) {
        if (pointState == null) {
            return "0/0";
        }
        return displayProgressText(pointState.traceProgress(), pointState.totalTraceSteps());
    }
}
