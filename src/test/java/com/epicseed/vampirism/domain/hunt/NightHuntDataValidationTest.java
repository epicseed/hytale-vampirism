package com.epicseed.vampirism.domain.hunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;

import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class NightHuntDataValidationTest {

    @Test
    void actualProgressionJsonPassesStrictValidation() {
        NightHuntValidationReport report = NightHuntProgressionRegistry.validateResource();
        NightHuntProgressionRegistry registry = new NightHuntProgressionRegistry();

        assertFalse(report.usedCompatibilityResource());
        assertFalse(report.usedFallbackData());
        assertTrue(report.warnings().isEmpty(), () -> String.join("\n", report.warnings()));
        assertTrue(report.errors().isEmpty(), () -> String.join("\n", report.errors()));
        assertEquals(3, registry.snapshot().contractModes().size());
        assertEquals(3, registry.snapshot().preparations().size());
        assertEquals(3, registry.snapshot().ranks().size());
        assertEquals(4, registry.snapshot().archetypes().size());
        assertTrue(registry.snapshot().hasContractMode("dread"));
        assertTrue(registry.snapshot().hasPreparation("bloodhound-rite"));
        assertTrue(registry.snapshot().hasResolution("drain"));
    }

    @Test
    void actualSpawnJsonPassesStrictValidation() {
        NightHuntValidationReport report = NightHuntSpawnRegistry.validateResource();
        NightHuntSpawnRegistry registry = new NightHuntSpawnRegistry(null, NightHuntSpawnRegistry.class.getClassLoader());

        assertFalse(report.usedCompatibilityResource());
        assertFalse(report.usedFallbackData());
        assertTrue(report.warnings().isEmpty(), () -> String.join("\n", report.warnings()));
        assertTrue(report.errors().isEmpty(), () -> String.join("\n", report.errors()));
        assertEquals(12, registry.allSpawns().size());
        assertEquals(4, registry.getEligibleRouteEvents(new NightHuntSpawnRegistry.RouteEventContext(99, 99, false, 23, 3)).size());
        assertEquals(3, registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 2, "summoning")).size());
        assertTrue(registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 2, "summoning"))
                .stream().map(NightHuntSpawnRegistry.FailStateOption::id).collect(Collectors.toSet())
                .contains("trail-stumble"));
        assertEquals(3, registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 3, "summoning")).size());
        assertTrue(registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 3, "summoning"))
                .stream().map(NightHuntSpawnRegistry.FailStateOption::id).collect(Collectors.toSet())
                .contains("sovereign-retaliation"));
        assertTrue(registry.getEligibleEnvironments(
                new NightHuntSpawnRegistry.EnvironmentContext(99, 99, false, 23, 3, 110d, "any", "pursuit", "bloodhound-rite"))
                .stream().map(NightHuntSpawnRegistry.EnvironmentOption::id).collect(Collectors.toSet())
                .contains("frostbound-heights"));
        assertTrue(registry.getEligibleEnvironments(
                new NightHuntSpawnRegistry.EnvironmentContext(99, 99, false, 23, 3, 60d, "any", "pursuit", "bloodhound-rite"))
                .stream().map(NightHuntSpawnRegistry.EnvironmentOption::id).collect(Collectors.toSet())
                .contains("lowland-mire"));
        assertTrue(registry.getEligibleEnvironments(
                new NightHuntSpawnRegistry.EnvironmentContext(99, 99, false, 23, 3, 110d, "any", "dread", "dread-mantle"))
                .stream().map(NightHuntSpawnRegistry.EnvironmentOption::id).collect(Collectors.toSet())
                .contains("consecrated-threshold"));
        assertTrue(registry.getEligibleEncounterBeats(
                new NightHuntSpawnRegistry.EncounterBeatContext(99, 99, false, 23, 3,
                        "lowland-mire", "dread", "stalker", "beast"))
                .stream().map(NightHuntSpawnRegistry.EncounterBeatOption::id).collect(Collectors.toSet())
                .contains("mire-sink"));
        assertTrue(registry.hasEnvironmentId("consecrated-threshold"));
        assertTrue(registry.hasEncounterBeatId("sacred-ground"));
        assertTrue(registry.hasSpawnRoleId("Emberwulf"));
        assertTrue(registry.hasPreyFamily("vermin"));
    }
}
