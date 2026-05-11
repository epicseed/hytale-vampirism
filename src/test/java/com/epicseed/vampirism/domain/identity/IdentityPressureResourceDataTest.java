package com.epicseed.vampirism.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IdentityPressureResourceDataTest {

    @Test
    void shippedIdentityPressureDataCoversCurrentAgeAndLineagePressureRules() {
        IdentityPressureRegistry registry = new IdentityPressureRegistry(
                IdentityPressureRegistry.DEFAULT_RESOURCE_PATH,
                IdentityPressureRegistry.class.getClassLoader());

        assertEquals(1.0d, registry.hunterPressureMultiplier("fledgling"));
        assertEquals(1.2d, registry.hunterPressureMultiplier("elder"));
        assertEquals(1.5d, registry.hunterPressureMultiplier("ancient"));
        assertEquals(1.3d, registry.worldThreatEscalationMultiplier("outlander"));
        assertEquals(0.8d, registry.worldThreatEscalationMultiplier("mesmerist"));
        assertEquals(0.8d, registry.worldThreatEscalationMultiplier("voidspawn"));
        assertEquals(0.8d, registry.worldThreatEscalationMultiplier("voidtaken"));
    }
}
