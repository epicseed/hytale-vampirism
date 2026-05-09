package com.epicseed.vampirism.domain.ritual.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class VampiricRitualSelectionServiceTest {

    @Test
    void storesSelectionsPerAnchorAndClearsThem() {
        UUID uuid = UUID.randomUUID();
        VampiricRitualSelectionService service = new VampiricRitualSelectionService();

        service.select(uuid, "Furniture_Ancient_Coffin", "awakening");
        service.select(uuid, "Furniture_Blood_Altar", "soul_exchange");

        assertEquals("awakening", service.selectedRitual(uuid, "Furniture_Ancient_Coffin").orElseThrow());
        assertEquals("soul_exchange", service.selectedRitual(uuid, "Furniture_Blood_Altar").orElseThrow());
        assertEquals(Map.of(
                "Furniture_Ancient_Coffin", "awakening",
                "Furniture_Blood_Altar", "soul_exchange"), service.snapshot(uuid));

        service.select(uuid, "Furniture_Ancient_Coffin", null);

        assertTrue(service.selectedRitual(uuid, "Furniture_Ancient_Coffin").isEmpty());
        assertEquals(Map.of("Furniture_Blood_Altar", "soul_exchange"), service.snapshot(uuid));

        service.clearPlayer(uuid);

        assertTrue(service.snapshot(uuid).isEmpty());
    }
}
