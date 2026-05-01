package com.epicseed.vampirism.interop;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.interop.classification.EntityClassificationRegistry;

class VampirismClassificationsTest {

    private EntityClassificationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = EntityClassificationRegistry.global();
        registry.clearProviders();
    }

    @AfterEach
    void tearDown() {
        registry.clearProviders();
    }

    @Test
    void helperMethodsReturnFalseForNullEntityUuid() {
        assertFalse(VampirismClassifications.isVampiric(null));
        assertFalse(VampirismClassifications.isPermanentVampire(null));
        assertFalse(VampirismClassifications.isInfected(null));
    }

    @Test
    void helperMethodsQueryExpectedClassificationIdsThroughGlobalRegistry() {
        UUID vampiricUuid = UUID.randomUUID();
        UUID permanentVampireUuid = UUID.randomUUID();
        UUID infectedUuid = UUID.randomUUID();

        registry.registerProvider("test:vampirism-helper", (entityUuid, classificationId) ->
                vampiricUuid.equals(entityUuid) && VampirismClassifications.VAMPIRIC.equals(classificationId)
                        || permanentVampireUuid.equals(entityUuid) && VampirismClassifications.VAMPIRE.equals(classificationId)
                        || infectedUuid.equals(entityUuid) && VampirismClassifications.INFECTED.equals(classificationId));

        assertTrue(VampirismClassifications.isVampiric(vampiricUuid));
        assertFalse(VampirismClassifications.isPermanentVampire(vampiricUuid));
        assertFalse(VampirismClassifications.isInfected(vampiricUuid));

        assertTrue(VampirismClassifications.isPermanentVampire(permanentVampireUuid));
        assertFalse(VampirismClassifications.isVampiric(permanentVampireUuid));
        assertFalse(VampirismClassifications.isInfected(permanentVampireUuid));

        assertTrue(VampirismClassifications.isInfected(infectedUuid));
        assertFalse(VampirismClassifications.isVampiric(infectedUuid));
        assertFalse(VampirismClassifications.isPermanentVampire(infectedUuid));
    }

    @Test
    void globalRegistryTemporaryProviderCanBeRegisteredAndUnregistered() {
        UUID entityUuid = UUID.randomUUID();

        registry.registerProvider("test:temporary", (uuid, classificationId) ->
                entityUuid.equals(uuid) && VampirismClassifications.VAMPIRIC.equals(classificationId));

        assertTrue(registry.hasClassification(entityUuid, VampirismClassifications.VAMPIRIC));
        assertTrue(VampirismClassifications.isVampiric(entityUuid));

        registry.unregisterProvider("test:temporary");

        assertFalse(registry.hasClassification(entityUuid, VampirismClassifications.VAMPIRIC));
        assertFalse(VampirismClassifications.isVampiric(entityUuid));
    }

    @Test
    void globalRegistryReplacingProviderWithSameIdUpdatesInteropResult() {
        UUID entityUuid = UUID.randomUUID();

        registry.registerProvider("test:replaceable", (uuid, classificationId) -> false);
        assertFalse(VampirismClassifications.isInfected(entityUuid));

        registry.registerProvider("test:replaceable", (uuid, classificationId) ->
                entityUuid.equals(uuid) && VampirismClassifications.INFECTED.equals(classificationId));

        assertTrue(VampirismClassifications.isInfected(entityUuid));
    }
}
