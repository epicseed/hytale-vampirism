package com.epicseed.vampirism.hytale.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RitualOfferingSurfaceInteractionTest {

    @Test
    void exposesExplicitFailureMessagesForOfferingSurfaceUx() {
        assertEquals(
                "Offering: Hold a ritual offering to place it here.",
                RitualOfferingSurfaceInteraction.EMPTY_SURFACE_MESSAGE);
        assertEquals(
                "Offering: Only the ritual owner can use this offering surface.",
                RitualOfferingSurfaceInteraction.WRONG_OWNER_MESSAGE);
        assertEquals(
                "Offering: Ritual offering surfaces are unavailable right now.",
                RitualOfferingSurfaceInteraction.RUNTIME_UNAVAILABLE_MESSAGE);
        assertEquals(
                "Offering: The ritual could not take the held item, so nothing was placed.",
                RitualOfferingSurfaceInteraction.PLACE_COMMIT_FAILED_MESSAGE);
        assertEquals(
                "Offering: The offering could not be returned right now.",
                RitualOfferingSurfaceInteraction.RECLAIM_COMMIT_FAILED_MESSAGE);
    }
}
