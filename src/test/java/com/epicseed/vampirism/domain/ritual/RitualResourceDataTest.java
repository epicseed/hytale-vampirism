package com.epicseed.vampirism.domain.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

    private static VampiricRitualTemplatePoint point(VampiricRitualTemplate template, String pointId) {
        return template.points().stream()
                .filter(point -> point.id().equals(pointId))
                .findFirst()
                .orElseThrow();
    }
}
