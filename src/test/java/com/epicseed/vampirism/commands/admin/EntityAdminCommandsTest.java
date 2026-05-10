package com.epicseed.vampirism.commands.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EntityAdminCommandsTest {
    @Test
    void shouldRemoveCandidateOnlyForNearbyNonPlayers() {
        assertFalse(EntityClearCommandSupport.shouldRemoveCandidate(true, true, false, 1.0d, 25.0d));
        assertFalse(EntityClearCommandSupport.shouldRemoveCandidate(false, false, false, 1.0d, 25.0d));
        assertFalse(EntityClearCommandSupport.shouldRemoveCandidate(false, true, true, 1.0d, 25.0d));
        assertFalse(EntityClearCommandSupport.shouldRemoveCandidate(false, true, false, 36.0d, 25.0d));
        assertTrue(EntityClearCommandSupport.shouldRemoveCandidate(false, true, false, 9.0d, 25.0d));
    }

    @Test
    void validatesRadiusAndFormatsSummary() {
        assertFalse(EntityClearCommandSupport.isValidRadius(0f));
        assertFalse(EntityClearCommandSupport.isValidRadius(-1f));
        assertFalse(EntityClearCommandSupport.isValidRadius(Float.NaN));
        assertTrue(EntityClearCommandSupport.isValidRadius(16f));

        assertEquals(
                "Removed 3 nearby non-player entities within 16.0 blocks. Skipped 2 player entities for safety.",
                EntityClearCommandSupport.formatSummary(3, 2, 16f));
    }
}
