package com.epicseed.epiccore.loadout;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GrantSuppressionTrackerTest {

    @Test
    void suppressionExpiresAutomatically() throws InterruptedException {
        GrantSuppressionTracker<String> tracker = new GrantSuppressionTracker<>();

        tracker.suppress("player", 25L);

        assertTrue(tracker.isSuppressed("player"));
        Thread.sleep(40L);
        assertFalse(tracker.isSuppressed("player"));
    }

    @Test
    void clearRemovesSuppressionImmediately() {
        GrantSuppressionTracker<String> tracker = new GrantSuppressionTracker<>();
        tracker.suppress("player", 1_000L);

        tracker.clear("player");

        assertFalse(tracker.isSuppressed("player"));
    }
}
