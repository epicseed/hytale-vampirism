package com.epicseed.vampirism.domain.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualDefinition;

class LineageResourceDataTest {

    @Test
    void shippedLineagesReserveAffinityGatesForSelectedSpecializations() {
        VampiricLineageRegistry registry = new VampiricLineageRegistry();

        VampiricLineageDefinition outlander = registry.lineage("outlander").orElseThrow();
        VampiricLineageDefinition mesmerist = registry.lineage("mesmerist").orElseThrow();
        VampiricLineageDefinition voidspawn = registry.lineage("voidspawn").orElseThrow();
        VampiricLineageDefinition voidtaken = registry.lineage("voidtaken").orElseThrow();

        assertTrue(outlander.requirements().requiredCompletedRitualIds().contains("summon_familiar"));
        assertEquals(
                List.of(new VampiricRitualDefinition.AffinityRequirement("vermin", 1)),
                outlander.requirements().requiredAffinities());
        assertEquals(Set.of("awakening"), voidspawn.requirements().requiredCompletedRitualIds());
        assertTrue(voidspawn.requirements().requiredAffinities().isEmpty());
        assertTrue(mesmerist.requirements().requiredCompletedRitualIds().contains("mind_weave"));
        assertEquals(
                List.of(new VampiricRitualDefinition.AffinityRequirement("humanoid", 1)),
                mesmerist.requirements().requiredAffinities());
        assertTrue(voidtaken.requirements().requiredCompletedRitualIds().contains("soul_exchange"));
        assertEquals(
                List.of(new VampiricRitualDefinition.AffinityRequirement("monstrous", 1)),
                voidtaken.requirements().requiredAffinities());
    }
}
