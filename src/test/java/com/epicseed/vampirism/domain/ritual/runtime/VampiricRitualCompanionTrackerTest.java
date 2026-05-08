package com.epicseed.vampirism.domain.ritual.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class VampiricRitualCompanionTrackerTest {

    @Test
    void expiresTrackedCompanions() {
        UUID owner = UUID.randomUUID();
        VampiricRitualCompanionTracker.replace(owner, dummyRef(), "Fox", 1_000L);

        assertTrue(VampiricRitualCompanionTracker.active(owner, 900L).isPresent());
        assertNull(VampiricRitualCompanionTracker.expire(owner, 999L));

        var expired = VampiricRitualCompanionTracker.expire(owner, 1_000L);
        assertEquals("Fox", expired.roleId());
        assertFalse(VampiricRitualCompanionTracker.active(owner, 1_001L).isPresent());
    }

    @Test
    void replacingCompanionReturnsPreviousState() {
        UUID owner = UUID.randomUUID();
        VampiricRitualCompanionTracker.replace(owner, dummyRef(), "Fox", 1_000L);

        var previous = VampiricRitualCompanionTracker.replace(owner, dummyRef(), "Fox", 2_000L);

        assertEquals(1_000L, previous.expiresAtMs());
        assertEquals(2_000L, VampiricRitualCompanionTracker.active(owner, 1_500L).orElseThrow().expiresAtMs());
        VampiricRitualCompanionTracker.clearPlayer(owner);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> dummyRef() {
        return (com.hypixel.hytale.component.Ref) new com.hypixel.hytale.component.Ref<>(null);
    }
}
