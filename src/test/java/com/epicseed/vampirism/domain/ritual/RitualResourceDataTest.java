package com.epicseed.vampirism.domain.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.vampirism.domain.progression.VampiricProgressionProofs;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class RitualResourceDataTest {

    @Test
    void shippedGlyphDataKeepsFangAndMirrorSymbolsAligned() {
        VampiricRitualTemplateRegistry registry = new VampiricRitualTemplateRegistry();

        VampiricRitualTemplate awakening = registry.template("awakening").orElseThrow();
        VampiricRitualTemplate markPrey = registry.template("mark_prey").orElseThrow();
        VampiricRitualTemplate veilOfNight = registry.template("veil_of_night").orElseThrow();

        assertEquals("fang_wake", point(awakening, "north").symbolId());
        assertEquals("mirror_fang", point(markPrey, "east").symbolId());
        assertEquals("mirror_fang", point(veilOfNight, "north_east").symbolId());
    }

    @Test
    void shippedTemplatesExposeExplicitCancelPolicies() {
        VampiricRitualTemplateRegistry registry = new VampiricRitualTemplateRegistry();
        VampiricRitualCancelPolicy expected = VampiricRitualCancelPolicy.shippedDefaults();

        registry.templates().forEach(template -> {
            assertEquals(expected, template.cancelPolicy(), template.ritualId());
        });
    }

    @Test
    void shippedDefinitionsExposeAffinityAndOfferingRequirements() {
        VampiricRitualRegistry registry = new VampiricRitualRegistry();

        VampiricRitualDefinition summonFamiliar = registry.definition("summon_familiar").orElseThrow();
        VampiricRitualDefinition soulExchange = registry.definition("soul_exchange").orElseThrow();
        VampiricRitualDefinition mindWeave = registry.definition("mind_weave").orElseThrow();
        VampiricRitualDefinition veilOfNight = registry.definition("veil_of_night").orElseThrow();

        assertEquals(36, summonFamiliar.minBlood());
        assertFalse(summonFamiliar.objectives().isEmpty());
        VampiricRitualDefinition.Objective objective = summonFamiliar.objectives().get(0);
        assertEquals("bind_familiar", objective.id());
        assertEquals("Ingredient_Voidheart", objective.offering().itemId());
        assertEquals(VampiricRitualOfferingSurfacePolicy.ANY_POINT_OR_CENTER, objective.offering().surfacePolicy());
        assertEquals(1, summonFamiliar.requiredAffinities().size());
        assertEquals("vermin", summonFamiliar.requiredAffinities().get(0).affinityId());
        assertEquals(1, summonFamiliar.requiredAffinities().get(0).minAmount());
        assertEquals(1, soulExchange.requiredAffinities().size());
        assertEquals("monstrous", soulExchange.requiredAffinities().get(0).affinityId());
        assertEquals(1, soulExchange.requiredAffinities().get(0).minAmount());
        assertEquals(1, mindWeave.requiredAffinities().size());
        assertEquals("humanoid", mindWeave.requiredAffinities().get(0).affinityId());
        assertEquals(2, mindWeave.minCompletedNightHunts());
        assertEquals(Set.of(VampiricProgressionProofs.FIRST_NIGHT_HUNT_COMPLETION), veilOfNight.requiredProofIds());
    }

    @Test
    void shippedDefinitionsKeepEpiccoreRitualCoverage() {
        VampiricRitualRegistry shippedRegistry = new VampiricRitualRegistry();
        VampiricRitualRegistry defaultRegistry = VampiricRitualRegistry.defaultAscensionRegistry();

        assertEquals(
                defaultRegistry.definitions().keySet().stream().sorted().toList(),
                shippedRegistry.definitions().keySet().stream().sorted().toList());
    }

    @Test
    void shippedTemplatesRemainResolvableForTheirAnchors() {
        VampiricRitualTemplateRegistry templateRegistry = new VampiricRitualTemplateRegistry();
        VampiricRitualRuntimeService runtimeService = new VampiricRitualRuntimeService(
                new VampiricRitualService(new VampiricRitualRegistry(), new NoOpRewardPort()),
                templateRegistry);

        List<String> resolvedRitualIds = runtimeService.listRitualsForAnchor("Furniture_Ancient_Coffin").stream()
                .map(VampiricRitualRuntimeService.ResolvedAnchorRitual::ritualId)
                .toList();

        templateRegistry.templates().forEach(template -> assertTrue(
                resolvedRitualIds.contains(template.ritualId()),
                () -> "missing anchor resolution for " + template.ritualId()));
    }

    private static VampiricRitualTemplatePoint point(VampiricRitualTemplate template, String pointId) {
        return template.points().stream()
                .filter(point -> point.id().equals(pointId))
                .findFirst()
                .orElseThrow();
    }

    private static final class NoOpRewardPort implements VampiricRitualRewardPort {

        @Override
        public void grantSkill(UUID uuid, String skillId) {
        }

        @Override
        public void addSkillPoints(UUID uuid, int amount) {
        }

        @Override
        public void adjustBlood(UUID uuid, int delta) {
        }

        @Override
        public void setAgeTier(UUID uuid, String ageTierId) {
        }

        @Override
        public void setLineage(UUID uuid, String lineageId) {
        }

        @Override
        public void applySideEffect(UUID uuid, String ritualId, String sideEffectId) {
        }
    }
}
