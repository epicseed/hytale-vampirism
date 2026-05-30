package com.epicseed.vampirism.hytale.interaction;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.ui.ProgressionPageFactory;
import com.epicseed.vampirism.skill.runtime.AbilityService;

public final class VampirismInteractionRuntime {

    private static final AtomicReference<Services> SERVICES = new AtomicReference<>();

    private VampirismInteractionRuntime() {
    }

    public static void install(@Nonnull Services services) {
        SERVICES.set(Objects.requireNonNull(services, "services"));
    }

    public static void clear() {
        SERVICES.set(null);
    }

    @Nullable
    public static Services current() {
        return SERVICES.get();
    }

    public record Services(@Nonnull VampirismRitualToolActions ritualToolActions,
                           @Nonnull AbilityService abilityService,
                           @Nonnull ProgressionPageFactory progressionPageFactory) {

        public Services {
            Objects.requireNonNull(ritualToolActions, "ritualToolActions");
            Objects.requireNonNull(abilityService, "abilityService");
            Objects.requireNonNull(progressionPageFactory, "progressionPageFactory");
        }
    }
}
