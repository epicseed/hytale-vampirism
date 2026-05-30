package com.epicseed.vampirism.domain.ritual.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import org.joml.Vector3d;
import org.joml.Vector3i;

class VampiricRitualOfferingRecoveryServiceTest {

    @Test
    void describesRecoveryAtCenterAndGlyphPositions() {
        VampiricRitualRuntimeSnapshot snapshot = snapshot();
        List<VampiricRitualOfferingRecoveryService.RecoveredOffering> offerings =
                VampiricRitualOfferingRecoveryService.describe(new VampiricRitualRuntimeService.OfferingRecovery(
                        snapshot,
                        Map.of(
                                "center", "Vampirism_Item_VoidHeart",
                                "point:north", "Vampirism_Item_VoidHeart")));

        assertEquals(2, offerings.size());
        assertVector(
                VampiricRitualGlyphPresentationService.offeringDropPosition(snapshot, "center"),
                offering(offerings, "center").position());
        assertVector(
                VampiricRitualGlyphPresentationService.offeringDropPosition(snapshot, "point:north"),
                offering(offerings, "point:north").position());
    }

    @Test
    void ignoresRecoveryEntriesWithoutKnownPlacement() {
        List<VampiricRitualOfferingRecoveryService.RecoveredOffering> offerings =
                VampiricRitualOfferingRecoveryService.describe(new VampiricRitualRuntimeService.OfferingRecovery(
                        snapshot(),
                        Map.of(
                                "point:missing", "Vampirism_Item_VoidHeart",
                                "unknown", "Vampirism_Item_VoidHeart")));

        assertTrue(offerings.isEmpty());
    }

    private static VampiricRitualOfferingRecoveryService.RecoveredOffering offering(
            List<VampiricRitualOfferingRecoveryService.RecoveredOffering> offerings,
            String surfaceId) {
        return offerings.stream()
                .filter(offering -> offering.surfaceId().equals(surfaceId))
                .findFirst()
                .orElseThrow();
    }

    private static void assertVector(Vector3d expected, Vector3d actual) {
        assertEquals(expected.x(), actual.x(), 0.0001d);
        assertEquals(expected.y(), actual.y(), 0.0001d);
        assertEquals(expected.z(), actual.z(), 0.0001d);
    }

    private static VampiricRitualRuntimeSnapshot snapshot() {
        return new VampiricRitualRuntimeSnapshot(
                "summon_familiar",
                "Summon Familiar",
                "Furniture_Ancient_Coffin",
                new Vector3i(4, 64, -2),
                new Vector3d(4.5d, 64.15d, -1.5d),
                null,
                null,
                VampiricRitualRuntimePhase.PREPARING,
                false,
                1,
                2,
                100d,
                70d,
                10d,
                0d,
                0,
                0d,
                8d,
                List.of(),
                List.of(new VampiricRitualPointState(
                        "north",
                        new Vector3d(4.5d, 64.15d, -3.0d),
                        true,
                        1d,
                        "north_glyph",
                        "North Glyph",
                        4,
                        4,
                        false,
                        List.of(),
                        List.of())));
    }
}
