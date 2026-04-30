package com.epicseed.epiccore.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class AbilitySlotBindingsTest {

    @Test
    void storesAndExposesImmutableAbilitySlotBindings() {
        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        bindings.put("primary", "BloodSucker");

        AbilitySlotBindings.set(bindings);

        assertEquals("BloodSucker", AbilitySlotBindings.abilityFor("primary"));
        assertThrows(UnsupportedOperationException.class,
                () -> AbilitySlotBindings.all().put("secondary", "BatForm"));
    }
}
