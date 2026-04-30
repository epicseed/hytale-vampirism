package com.epicseed.epiccore.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ResourceGaugeValueTest {

    @Test
    void sanitizesAndFormatsDisplayText() {
        ResourceGaugeValue value = new ResourceGaugeValue(150, 100);

        assertEquals(100, value.currentValue());
        assertEquals(100, value.maxValue());
        assertEquals("100 / 100", value.displayText());
        assertEquals(1.0, value.fillRatio());
    }
}
