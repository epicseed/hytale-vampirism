package com.epicseed.vampirism.domain.ritual.runtime;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;

public enum VampiricRitualAnchorState {
    PREPARED("prepared"),
    BINDING("binding"),
    ACTIVE("active"),
    UNSTABLE("unstable"),
    COLLAPSE("collapse"),
    COMPLETE("complete");

    private final String displayName;

    VampiricRitualAnchorState(@Nonnull String displayName) {
        this.displayName = displayName;
    }

    @Nonnull
    public String displayName() {
        return displayName;
    }

    @Nonnull
    public static VampiricRitualAnchorState fromSnapshot(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        return fromPhase(snapshot.phase());
    }

    @Nonnull
    public static VampiricRitualAnchorState fromPhase(@Nonnull VampiricRitualRuntimePhase phase) {
        return switch (phase) {
            case PREPARING -> PREPARED;
            case BINDING -> BINDING;
            case CHANNELING -> ACTIVE;
            case UNSTABLE -> UNSTABLE;
            case SUCCESS -> COMPLETE;
            case COLLAPSE -> COLLAPSE;
        };
    }
}
