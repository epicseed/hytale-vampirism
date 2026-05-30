package com.epicseed.vampirism.bootstrap;

import com.epicseed.epiccore.hytale.runtime.ManagedPluginScheduler;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class ProfileAutosaveScheduler {

    private ProfileAutosaveScheduler() {
    }

    @Nullable
    static ManagedPluginScheduler create(@Nonnull String schedulerName,
                                         @Nonnull Duration interval,
                                         @Nonnull IntSupplier flushOnlinePlayers,
                                         @Nonnull BiConsumer<String, RuntimeException> failureReporter,
                                         @Nonnull IntConsumer savedProfilesReporter) {
        if (interval.isZero() || interval.isNegative()) {
            return null;
        }
        ManagedPluginScheduler scheduler = ManagedPluginScheduler.create(schedulerName, 1, failureReporter);
        scheduler.scheduleWithFixedDelay("profile-autosave", interval, interval, () -> {
            int savedProfiles = flushOnlinePlayers.getAsInt();
            if (savedProfiles > 0) {
                savedProfilesReporter.accept(savedProfiles);
            }
        });
        return scheduler;
    }
}
