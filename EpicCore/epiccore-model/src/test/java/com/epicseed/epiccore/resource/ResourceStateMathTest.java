package com.epicseed.epiccore.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResourceStateMathTest {

    @Test
    void clampsAndSanitizesValues() {
        assertEquals(1, ResourceStateMath.sanitizeMax(0));
        assertEquals(0, ResourceStateMath.clampCurrent(-5, 100));
        assertEquals(100, ResourceStateMath.clampCurrent(150, 100));
    }

    @Test
    void spendsAndAddsWithoutEscapingBounds() {
        assertEquals(60, ResourceStateMath.spend(100, 40));
        assertEquals(0, ResourceStateMath.spend(10, 50));
        assertEquals(90, ResourceStateMath.add(70, 90, 50));
        assertEquals(70, ResourceStateMath.add(70, 90, 0));
    }
}
