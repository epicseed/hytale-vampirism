package com.epicseed.epiccore.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class PlayerProgressProfileTest {

    @Test
    void sanitizeMigratesLegacyBindingsIntoPresetZero() {
        PlayerProgressProfile profile = new PlayerProgressProfile();
        profile.relicBindings.put("primary", "BloodDash");

        profile.sanitize();

        assertEquals("BloodDash", profile.relicBindingsFor(0).get("primary"));
        assertEquals("BloodDash", profile.relicBindings.get("primary"));
        assertTrue(profile.relicPresets.containsKey("0"));
    }

    @Test
    void sanitizeMirrorsActivePresetIntoLegacyBindingField() {
        PlayerProgressProfile profile = new PlayerProgressProfile();
        profile.activeRelicPreset = 2;
        LinkedHashMap<String, String> presetTwo = new LinkedHashMap<>();
        presetTwo.put("ability1", "BatSwarm");
        profile.relicPresets.put("2", presetTwo);

        profile.sanitize();

        assertEquals(2, profile.activeRelicPreset);
        assertEquals("BatSwarm", profile.relicBindings.get("ability1"));
        assertEquals("BatSwarm", profile.activeRelicBindings().get("ability1"));
    }
}
