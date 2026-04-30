package com.epicseed.epiccore.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class StateEffectBindingsTest {

    @Test
    void storesAndExposesImmutableStateEffectBindings() {
        LinkedHashMap<String, String> bindings = new LinkedHashMap<>();
        bindings.put("IS_INVISIBLE", "Vampirism_Invisibility");

        StateEffectBindings.set(bindings);

        assertEquals("Vampirism_Invisibility", StateEffectBindings.effectIdFor("IS_INVISIBLE"));
        assertThrows(UnsupportedOperationException.class,
                () -> StateEffectBindings.all().put("IS_IN_BAT_FORM", "Potion_Morph_Bat"));
    }
}
