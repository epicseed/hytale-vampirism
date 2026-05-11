package com.epicseed.vampirism.domain.hunt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.ui.NightHuntPreparationAffinityContent;

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
        assertEquals(4, registry.snapshot().preparations().size());
        assertEquals("bloodhound-mire-signature",
                registry.snapshot().preparationOrDefault("bloodhound-rite").affinitySignature().id());
        assertEquals("bloodhound-mire",
                registry.snapshot().preparationOrDefault("bloodhound-rite").affinitySignature().environmentId());
        assertEquals("siphon-warren-signature",
                registry.snapshot().preparationOrDefault("siphon-rite").affinitySignature().id());
        assertEquals("drain-brood-spiral",
                registry.snapshot().preparationOrDefault("siphon-rite").affinitySignature().encounterBeatId());
        assertEquals("omen-howl",
                registry.snapshot().preparationOrDefault("dread-mantle").affinitySignature().encounterBeatId());
        assertEquals("shadow-court-signature",
                registry.snapshot().preparationOrDefault("shadow-pact").affinitySignature().id());
        assertEquals("whisper-court",
                registry.snapshot().preparationOrDefault("shadow-pact").affinitySignature().environmentId());
        assertEquals(3, registry.snapshot().ranks().size());
        assertEquals(4, registry.snapshot().archetypes().size());
        assertTrue(registry.snapshot().hasContractMode("dread"));
        assertTrue(registry.snapshot().hasPreparation("bloodhound-rite"));
        assertTrue(registry.snapshot().hasResolution("drain"));
        assertEquals(4, NightHuntPreparationAffinityContent.snapshot().byPreparationId().size());
        assertEquals("beast",
                NightHuntPreparationAffinityContent.focusForPreparation("bloodhound-rite").preyFamilyId());
        assertEquals("vermin",
                NightHuntPreparationAffinityContent.focusForPreparation("siphon-rite").preyFamilyId());
        assertEquals("monstrous",
                NightHuntPreparationAffinityContent.focusForPreparation("dread-mantle").preyFamilyId());
        assertEquals("humanoid",
                NightHuntPreparationAffinityContent.focusForPreparation("shadow-pact").preyFamilyId());
        assertEquals("shadow-pact",
                NightHuntPreparationAffinityContent.focusForPreyFamily("humanoid").preparationId());
    }

    @Test
    void actualSpawnJsonPassesStrictValidation() {
        NightHuntValidationReport report = NightHuntSpawnRegistry.validateResource();
        NightHuntSpawnRegistry registry = new NightHuntSpawnRegistry(null, NightHuntSpawnRegistry.class.getClassLoader());

        assertFalse(report.usedCompatibilityResource());
        assertFalse(report.usedFallbackData());
        assertTrue(report.warnings().isEmpty(), () -> String.join("\n", report.warnings()));
        assertTrue(report.errors().isEmpty(), () -> String.join("\n", report.errors()));
        assertEquals(13, registry.allSpawns().size());
        assertEquals(5, registry.allCasefiles().size());
        assertEquals(6, registry.getEligibleRouteEvents(new NightHuntSpawnRegistry.RouteEventContext(99, 99, false, 23, 3)).size());
        assertEquals(5, registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 2, "summoning")).size());
        assertTrue(registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 2, "summoning"))
                .stream().map(NightHuntSpawnRegistry.FailStateOption::id).collect(Collectors.toSet())
                .contains("trail-stumble"));
        assertTrue(registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 2, "summoning"))
                .stream().map(NightHuntSpawnRegistry.FailStateOption::id).collect(Collectors.toSet())
                .contains("dragnet-intercept"));
        assertTrue(registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 2, "summoning"))
                .stream().map(NightHuntSpawnRegistry.FailStateOption::id).collect(Collectors.toSet())
                .contains("ledger-seizure"));
        assertEquals(7, registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 3, "summoning")).size());
        assertTrue(registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 3, "summoning"))
                .stream().map(NightHuntSpawnRegistry.FailStateOption::id).collect(Collectors.toSet())
                .contains("sovereign-retaliation"));
        assertTrue(registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 3, "summoning"))
                .stream().map(NightHuntSpawnRegistry.FailStateOption::id).collect(Collectors.toSet())
                .contains("sunward-seizure"));
        assertTrue(registry.getEligibleFailStates(new NightHuntSpawnRegistry.FailStateContext(99, 99, false, 23, 3, "summoning"))
                .stream().map(NightHuntSpawnRegistry.FailStateOption::id).collect(Collectors.toSet())
                .contains("mirror-seizure"));
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
        assertTrue(registry.getEligibleEnvironments(
                new NightHuntSpawnRegistry.EnvironmentContext(99, 99, false, 23, 3, 68d, "any", "pursuit", "bloodhound-rite"))
                .stream().map(NightHuntSpawnRegistry.EnvironmentOption::id).collect(Collectors.toSet())
                .contains("bloodhound-mire"));
        assertTrue(registry.getEligibleEncounterBeats(
                new NightHuntSpawnRegistry.EncounterBeatContext(99, 99, false, 23, 3,
                        "bloodhound-mire", "pursuit", "stalker", "beast", "bloodhound-rite"))
                .stream().map(NightHuntSpawnRegistry.EncounterBeatOption::id).collect(Collectors.toSet())
                .contains("scent-lure-pack"));
        assertTrue(registry.getEligibleEnvironments(
                new NightHuntSpawnRegistry.EnvironmentContext(99, 99, false, 23, 2, 52d, "any", "siphon", "siphon-rite"))
                .stream().map(NightHuntSpawnRegistry.EnvironmentOption::id).collect(Collectors.toSet())
                .contains("blooded-warren"));
        assertTrue(registry.getEligibleEncounterBeats(
                new NightHuntSpawnRegistry.EncounterBeatContext(99, 99, false, 23, 2,
                        "blooded-warren", "siphon", "pack", "vermin", "siphon-rite"))
                .stream().map(NightHuntSpawnRegistry.EncounterBeatOption::id).collect(Collectors.toSet())
                .contains("drain-brood-spiral"));
        assertTrue(registry.getEligibleEncounterBeats(
                new NightHuntSpawnRegistry.EncounterBeatContext(99, 99, false, 23, 3,
                        "omen-threshold", "dread", "pack", "monstrous", "dread-mantle"))
                .stream().map(NightHuntSpawnRegistry.EncounterBeatOption::id).collect(Collectors.toSet())
                .contains("omen-howl"));
        assertTrue(registry.getEligibleEnvironments(
                new NightHuntSpawnRegistry.EnvironmentContext(99, 99, false, 23, 2, 64d, "any", "siphon", "shadow-pact"))
                .stream().map(NightHuntSpawnRegistry.EnvironmentOption::id).collect(Collectors.toSet())
                .contains("whisper-court"));
        assertTrue(registry.getEligibleEncounterBeats(
                new NightHuntSpawnRegistry.EncounterBeatContext(99, 99, false, 23, 2,
                        "whisper-court", "siphon", "stalker", "humanoid", "shadow-pact"))
                .stream().map(NightHuntSpawnRegistry.EncounterBeatOption::id).collect(Collectors.toSet())
                .contains("courtly-unraveling"));
        assertTrue(registry.hasEnvironmentId("consecrated-threshold"));
        assertTrue(registry.hasEncounterBeatId("sacred-ground"));
        String authoredSpawns = readResource("data/vampirism/night-hunt/spawns.json");
        assertTrue(authoredSpawns.contains("\"casefiles\""));
        assertTrue(authoredSpawns.contains("\"hunter-watch\""));
        assertTrue(authoredSpawns.contains("\"lantern-ledger\""));
        assertTrue(authoredSpawns.contains("\"sunward-sanction\""));
        assertTrue(authoredSpawns.contains("\"ledger-echo\""));
        assertTrue(authoredSpawns.contains("\"mirror-sanction\""));
        assertEquals("hunter-watch", registry.selectCasefile(new NightHuntSpawnRegistry.CasefileContext(0, 20)).id());
        assertEquals("lantern-ledger", registry.selectCasefile(new NightHuntSpawnRegistry.CasefileContext(1, 40)).id());
        assertEquals("sunward-sanction", registry.selectCasefile(new NightHuntSpawnRegistry.CasefileContext(2, 60)).id());
        assertEquals("ledger-echo", registry.selectCasefile(new NightHuntSpawnRegistry.CasefileContext(0, 35, "hunter-watch")).id());
        assertEquals("mirror-sanction", registry.selectCasefile(new NightHuntSpawnRegistry.CasefileContext(2, 60, "lantern-ledger")).id());
        assertEquals("lantern-shadowing", registry.selectCasefile(new NightHuntSpawnRegistry.CasefileContext(0, 20)).activeRouteEventId());
        assertEquals("sanction-relay", registry.selectCasefile(new NightHuntSpawnRegistry.CasefileContext(2, 60)).activeRouteEventId());
        assertTrue(registry.hasSpawnRoleId("Emberwulf"));
        assertTrue(registry.hasSpawnRoleId("Goblin_Thief"));
        assertTrue(registry.hasPreyFamily("vermin"));
        assertTrue(registry.hasPreyFamily("humanoid"));
        assertTrue(registry.hasRouteEventId("lantern-shadowing"));
        assertTrue(registry.hasRouteEventId("sanction-relay"));
    }

    private static String readResource(String path) {
        try (InputStream input = NightHuntDataValidationTest.class.getClassLoader().getResourceAsStream(path)) {
            assertTrue(input != null, () -> "Missing resource " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read resource " + path, ex);
        }
    }
}
