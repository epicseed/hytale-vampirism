package com.epicseed.vampirism.domain.relic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class RelicBindingServiceTest {

    @Test
    void normalizedRejectsNullAndBlankValues() {
        assertFalse(RelicBindingService.normalized(null).isPresent());
        assertFalse(RelicBindingService.normalized("").isPresent());
        assertFalse(RelicBindingService.normalized("   ").isPresent());
    }

    @Test
    void normalizedPreservesNonBlankAbilityId() {
        assertEquals("Ability", RelicBindingService.normalized("Ability").orElseThrow());
    }

    @Test
    void formatCooldownRoundsUpAndKeepsOneSecondMinimum() {
        assertEquals("1", RelicBindingService.formatCooldown(0));
        assertEquals("1", RelicBindingService.formatCooldown(1));
        assertEquals("2", RelicBindingService.formatCooldown(1001));
    }

    @Test
    void slotLabelUsesKnownLabelsAndUnknownFallback() {
        assertEquals("Primary", RelicBindingService.slotLabel("primary"));
        assertEquals("Secondary", RelicBindingService.slotLabel("secondary"));
        assertEquals("Ability 1", RelicBindingService.slotLabel("ability1"));
        assertEquals("Ability 2", RelicBindingService.slotLabel("ability2"));
        assertEquals("Ability 3", RelicBindingService.slotLabel("ability3"));
        assertEquals("custom", RelicBindingService.slotLabel("custom"));
    }

    @Test
    void presetHelpersExposeHumanLabelAndClampBounds() {
        assertEquals("Preset 1", RelicBindingService.presetLabel(0));
        assertEquals("Preset 4", RelicBindingService.presetLabel(3));
        assertEquals("No Offhand", RelicBindingService.presetLabel(4));
        assertEquals("Utility inactive", RelicBindingService.presetSubtitle(4, 4));
        assertEquals(0, RelicBindingService.clampPresetIndex(-1, 5));
        assertEquals(4, RelicBindingService.clampPresetIndex(7, 5));
        assertEquals(4, RelicBindingService.inactiveOffhandPresetIndex(4));
        assertEquals(2, RelicBindingService.presetIndexForUtilitySelection(2, -1, 4));
        assertEquals(4, RelicBindingService.presetIndexForUtilitySelection(-1, -1, 4));
        assertEquals(-1, RelicBindingService.presetIndexForUtilitySelection(9, -1, 4));
    }
}
