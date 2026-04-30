package com.epicseed.vampirism.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.epiccore.player.PlayerProgressProfile;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

class PlayerVampireProfileTest {

    @Test
    void sanitizeMigratesLegacyBindingsIntoPresetZero() {
        PlayerVampireProfile profile = new PlayerVampireProfile();
        profile.relicBindings.put("primary", "BloodDash");

        profile.sanitize();

        assertEquals("BloodDash", profile.relicBindingsFor(0).get("primary"));
        assertEquals("BloodDash", profile.relicBindings.get("primary"));
        assertTrue(profile.relicPresets.containsKey("0"));
    }

    @Test
    void sanitizeMirrorsActivePresetIntoLegacyBindingField() {
        PlayerVampireProfile profile = new PlayerVampireProfile();
        profile.activeRelicPreset = 2;
        LinkedHashMap<String, String> presetTwo = new LinkedHashMap<>();
        presetTwo.put("ability1", "BatSwarm");
        profile.relicPresets.put("2", presetTwo);

        profile.sanitize();

        assertEquals(2, profile.activeRelicPreset);
        assertEquals("BatSwarm", profile.relicBindings.get("ability1"));
        assertEquals("BatSwarm", profile.activeRelicBindings().get("ability1"));
    }

    @Test
    void progressProfileRoundTripsSharedProgressFields() {
        PlayerVampireProfile profile = new PlayerVampireProfile();
        profile.skillPoints = 7;
        profile.totalSpent = 3;
        profile.unlockedSkills.add("blood-dash");
        profile.activeRelicPreset = 1;
        profile.relicPresets.put("1", new LinkedHashMap<>(java.util.Map.of("ability1", "BatSwarm")));
        profile.abilityCooldowns.put("bat-form", 1500L);

        PlayerProgressProfile progress = profile.progressProfile();

        assertEquals(7, progress.skillPoints);
        assertEquals(3, progress.totalSpent);
        assertTrue(progress.unlockedSkills.contains("blood-dash"));
        assertEquals(1, progress.activeRelicPreset);
        assertEquals("BatSwarm", progress.relicPresets.get("1").get("ability1"));
        assertEquals(1500L, progress.abilityCooldowns.get("bat-form"));
    }

    @Test
    void applyProgressProfilePreservesLegacyFieldNamesThroughSharedModel() {
        PlayerProgressProfile progress = new PlayerProgressProfile();
        progress.skillPoints = 11;
        progress.totalSpent = 5;
        progress.unlockedSkills.add("night-sense");
        progress.activeRelicPreset = 2;
        progress.relicPresets.put("2", new LinkedHashMap<>(java.util.Map.of("ability2", "MistStep")));
        progress.abilityCooldowns.put("mist-step", 2200L);

        PlayerVampireProfile profile = new PlayerVampireProfile();
        profile.applyProgressProfile(progress);

        assertEquals(11, profile.skillPoints);
        assertEquals(5, profile.totalSpent);
        assertTrue(profile.unlockedSkills.contains("night-sense"));
        assertEquals(2, profile.activeRelicPreset);
        assertEquals("MistStep", profile.relicPresets.get("2").get("ability2"));
        assertEquals(2200L, profile.abilityCooldowns.get("mist-step"));
    }
}
