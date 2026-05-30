package com.epicseed.vampirism.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.epiccore.hytale.runtime.ManagedPluginScheduler;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class VampirismRuntimeAutosaveTest {

    @Test
    void disablesAutosaveWhenIntervalIsNotPositive() {
        assertNull(createScheduler(Duration.ZERO, () -> 0));
        assertNull(createScheduler(Duration.ofSeconds(-1), () -> 0));
    }

    @Test
    void runsAutosaveFlushOnManagedScheduler() throws InterruptedException {
        AtomicInteger flushes = new AtomicInteger();
        CountDownLatch firstFlush = new CountDownLatch(1);
        ManagedPluginScheduler scheduler = createScheduler(Duration.ofMillis(10), () -> {
            firstFlush.countDown();
            return flushes.incrementAndGet();
        });
        try {
            assertTrue(firstFlush.await(500, TimeUnit.MILLISECONDS));
            assertTrue(flushes.get() >= 1);
        } finally {
            scheduler.close(Duration.ofMillis(500));
        }
    }

    @Test
    void stopsAutosaveBeforeLaterFlushesCanRun() throws InterruptedException {
        AtomicInteger flushes = new AtomicInteger();
        CountDownLatch firstFlush = new CountDownLatch(1);
        ManagedPluginScheduler scheduler = createScheduler(Duration.ofMillis(10), () -> {
            firstFlush.countDown();
            return flushes.incrementAndGet();
        });

        assertTrue(firstFlush.await(500, TimeUnit.MILLISECONDS));
        scheduler.close(Duration.ofMillis(500));
        int afterClose = flushes.get();
        Thread.sleep(50);

        assertEquals(afterClose, flushes.get());
    }

    private static ManagedPluginScheduler createScheduler(Duration interval,
                                                          java.util.function.IntSupplier flushOnlinePlayers) {
        return ProfileAutosaveScheduler.create(
                "AutosaveTest",
                interval,
                flushOnlinePlayers,
                (taskName, failure) -> { },
                savedProfiles -> { });
    }
}
