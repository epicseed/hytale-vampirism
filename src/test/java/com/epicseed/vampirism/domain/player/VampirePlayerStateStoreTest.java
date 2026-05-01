package com.epicseed.vampirism.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.epiccore.player.PlayerProfileRepository;
import com.epicseed.epiccore.player.PlayerProgressStore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VampirePlayerStateStoreTest {

    private PlayerProgressStore<PlayerVampireProfile> progressStore;

    @BeforeEach
    void setUp() {
        InMemoryRepository repo = new InMemoryRepository();
        progressStore = new PlayerProgressStore<>(repo);
        VampirePlayerStateStore.init(progressStore);
    }

    // ── Blood ─────────────────────────────────────────────────────────────────

    @Test
    void setAndGetPersistedBloodRoundTrips() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().setPersistedBlood(uuid, 75);
        assertEquals(75, VampirePlayerStateStore.get().getPersistedBlood(uuid));
    }

    @Test
    void setPersistedBloodClampsNegativeToZero() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().setPersistedBlood(uuid, -10);
        assertEquals(0, VampirePlayerStateStore.get().getPersistedBlood(uuid));
    }

    @Test
    void defaultBloodIsHundred() {
        UUID uuid = UUID.randomUUID();
        assertEquals(100, VampirePlayerStateStore.get().getPersistedBlood(uuid));
    }

    // ── Infection ─────────────────────────────────────────────────────────────

    @Test
    void notInfectedByDefault() {
        UUID uuid = UUID.randomUUID();
        assertFalse(VampirePlayerStateStore.get().isInfected(uuid));
        assertEquals(0L, VampirePlayerStateStore.get().getInfectionExpiresAtMs(uuid));
        assertEquals(0L, VampirePlayerStateStore.get().getInfectionRemainingMs(uuid));
    }

    @Test
    void setInfectionExpiresAtMsInFutureIsInfected() {
        UUID uuid = UUID.randomUUID();
        long future = System.currentTimeMillis() + 60_000L;
        VampirePlayerStateStore.get().setInfectionExpiresAtMs(uuid, future);

        assertTrue(VampirePlayerStateStore.get().isInfected(uuid));
        assertTrue(VampirePlayerStateStore.get().getInfectionRemainingMs(uuid) > 0L);
        assertEquals(future, VampirePlayerStateStore.get().getInfectionExpiresAtMs(uuid));
    }

    @Test
    void setInfectionExpiresAtMsInPastIsNotInfected() {
        UUID uuid = UUID.randomUUID();
        long past = System.currentTimeMillis() - 1_000L;
        VampirePlayerStateStore.get().setInfectionExpiresAtMs(uuid, past);

        assertFalse(VampirePlayerStateStore.get().isInfected(uuid));
        assertEquals(0L, VampirePlayerStateStore.get().getInfectionRemainingMs(uuid));
    }

    @Test
    void clearInfectionRemovesActiveInfection() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().setInfectionExpiresAtMs(uuid, System.currentTimeMillis() + 60_000L);
        assertTrue(VampirePlayerStateStore.get().isInfected(uuid));

        VampirePlayerStateStore.get().clearInfection(uuid);
        assertFalse(VampirePlayerStateStore.get().isInfected(uuid));
        assertEquals(0L, VampirePlayerStateStore.get().getInfectionExpiresAtMs(uuid));
    }

    @Test
    void setInfectionClampsNegativeExpiryToZero() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().setInfectionExpiresAtMs(uuid, -500L);
        assertEquals(0L, VampirePlayerStateStore.get().getInfectionExpiresAtMs(uuid));
        assertFalse(VampirePlayerStateStore.get().isInfected(uuid));
    }

    // ── Night Hunt ────────────────────────────────────────────────────────────

    @Test
    void completedNightHuntsDefaultsToZero() {
        UUID uuid = UUID.randomUUID();
        assertEquals(0, VampirePlayerStateStore.get().getCompletedNightHunts(uuid));
    }

    @Test
    void incrementCompletedNightHuntsAddsOne() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().incrementCompletedNightHunts(uuid);
        VampirePlayerStateStore.get().incrementCompletedNightHunts(uuid);
        assertEquals(2, VampirePlayerStateStore.get().getCompletedNightHunts(uuid));
    }

    @Test
    void nightHuntCooldownRoundTrips() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().setPersistedNightHuntCooldownMs(uuid, 30_000L);
        assertEquals(30_000L, VampirePlayerStateStore.get().getPersistedNightHuntCooldownMs(uuid));
    }

    @Test
    void nightHuntCooldownClampsNegativeToZero() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().setPersistedNightHuntCooldownMs(uuid, -500L);
        assertEquals(0L, VampirePlayerStateStore.get().getPersistedNightHuntCooldownMs(uuid));
    }

    @Test
    void stateIsolatedPerPlayer() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        VampirePlayerStateStore.get().setPersistedBlood(alice, 50);
        VampirePlayerStateStore.get().setPersistedBlood(bob, 80);
        VampirePlayerStateStore.get().setInfectionExpiresAtMs(alice, System.currentTimeMillis() + 60_000L);

        assertEquals(50, VampirePlayerStateStore.get().getPersistedBlood(alice));
        assertEquals(80, VampirePlayerStateStore.get().getPersistedBlood(bob));
        assertTrue(VampirePlayerStateStore.get().isInfected(alice));
        assertFalse(VampirePlayerStateStore.get().isInfected(bob));
    }

    @Test
    void sharedStoreReadsAndWritesAreMutuallyVisible() {
        UUID uuid = UUID.randomUUID();
        progressStore.onPlayerConnect(uuid);

        // Write via VampirePlayerStateStore
        VampirePlayerStateStore.get().setPersistedBlood(uuid, 42);

        // Read back via the raw store to confirm same cache is used
        int blood = progressStore.readProfile(uuid, profile -> profile.blood);
        assertEquals(42, blood);
    }

    // ── In-memory repository for tests ────────────────────────────────────────

    private static final class InMemoryRepository implements PlayerProfileRepository<PlayerVampireProfile> {
        final Map<UUID, PlayerVampireProfile> storage = new HashMap<>();

        @Override
        public PlayerVampireProfile load(UUID uuid) {
            return storage.getOrDefault(uuid, new PlayerVampireProfile());
        }

        @Override
        public void save(UUID uuid, PlayerVampireProfile profile) {
            storage.put(uuid, profile);
        }
    }
}
