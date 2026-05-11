package com.epicseed.vampirism.commands.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.hunt.NightHuntProgressionRegistry;

class AffinityAdminCommandSupportTest {

    @Test
    void describeLanesIncludesAuthoredAndStoredOnlyAffinities() {
        var lanes = AffinityAdminCommandSupport.describeLanes(
                Map.of("beast", 2, "void", 1),
                snapshot(
                        preparation("predatory-stalking", "Predatory Stalking", "beast"),
                        preparation("grave-watch", "Grave Watch", "beast"),
                        preparation("siphon-lattice", "Siphon Lattice", "vermin")));

        assertEquals(3, lanes.size());
        assertEquals("beast", lanes.get(0).affinityId());
        assertEquals(2, lanes.get(0).currentAmount());
        assertEquals(List.of("Predatory Stalking", "Grave Watch"), lanes.get(0).preparationDisplayNames());
        assertEquals("vermin", lanes.get(1).affinityId());
        assertEquals(0, lanes.get(1).currentAmount());
        assertTrue(lanes.get(1).authored());
        assertEquals("void", lanes.get(2).affinityId());
        assertEquals(1, lanes.get(2).currentAmount());
        assertFalse(lanes.get(2).authored());
    }

    @Test
    void summarizeAffinitiesKeepsAuthoredOrderAndIncludesZeroes() {
        String summary = AffinityAdminCommandSupport.summarizeAffinities(
                Map.of("beast", 2, "void", 1),
                snapshot(
                        preparation("predatory-stalking", "Predatory Stalking", "beast"),
                        preparation("siphon-lattice", "Siphon Lattice", "vermin")));

        assertEquals("Beast=2 | Vermin=0 | Void=1", summary);
    }

    @Test
    void normalizeAffinityIdTrimsAndLowercases() {
        assertEquals("humanoid", AffinityAdminCommandSupport.normalizeAffinityId("  HuManoid  "));
        assertNull(AffinityAdminCommandSupport.normalizeAffinityId("   "));
    }

    private static NightHuntProgressionRegistry.Snapshot snapshot(
            NightHuntProgressionRegistry.PreparationDefinition... preparations) {
        return new NightHuntProgressionRegistry.Snapshot(
                new NightHuntProgressionRegistry.RewardRules(
                        0,
                        0,
                        0,
                        0,
                        0,
                        1.0f,
                        0,
                        0,
                        new NightHuntProgressionRegistry.PressureResonanceAscensionBonus(
                                null,
                                null,
                                false,
                                false,
                                0,
                                0L,
                                0,
                                0,
                                0)),
                List.of(),
                List.of(),
                List.of(),
                List.of(preparations));
    }

    private static NightHuntProgressionRegistry.PreparationDefinition preparation(String id,
                                                                                  String displayName,
                                                                                  String affinityFocusId) {
        return new NightHuntProgressionRegistry.PreparationDefinition(
                id,
                displayName,
                displayName + " description",
                "drain",
                0,
                0,
                affinityFocusId,
                0,
                0);
    }
}
