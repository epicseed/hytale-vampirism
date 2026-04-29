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
}
