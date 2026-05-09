package com.epicseed.vampirism.domain.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        registry.templates().forEach(template -> {
            assertEquals(45d, template.cancelPolicy().timeoutSeconds(), template.ritualId());
            assertEquals(8d, template.cancelPolicy().maxDistanceFromAnchor(), template.ritualId());
            assertEquals(1d, template.cancelPolicy().distanceGraceSeconds(), template.ritualId());
            assertTrue(template.cancelPolicy().cancelIfAnchorInvalid(), template.ritualId());
            assertTrue(template.cancelPolicy().cancelOnUnequipTool(), template.ritualId());
            assertTrue(template.cancelPolicy().cancelOnOwnerDeath(), template.ritualId());
        });
    }

    private static VampiricRitualTemplatePoint point(VampiricRitualTemplate template, String pointId) {
        return template.points().stream()
                .filter(point -> point.id().equals(pointId))
                .findFirst()
                .orElseThrow();
    }
}
