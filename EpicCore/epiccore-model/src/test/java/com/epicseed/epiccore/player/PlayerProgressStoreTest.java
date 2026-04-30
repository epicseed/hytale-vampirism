package com.epicseed.epiccore.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

class PlayerProgressStoreTest {

    @Test
    void disconnectPersistsSharedProgressCooldownsAndRelicState() {
        FakeRepository repository = new FakeRepository();
        PlayerProgressStore<FakeProfile> store = new PlayerProgressStore<>(repository);
        UUID uuid = UUID.randomUUID();

        store.onPlayerConnect(uuid);
        store.addSkillPoints(uuid, 5);
        store.setRelicBindings(uuid, 0, Map.of("primary", "BloodDash"));
        store.setPersistedAbilityCooldowns(uuid, Map.of("BatForm", 1500L));
        store.onPlayerDisconnect(uuid);

        FakeProfile saved = repository.storage.get(uuid);
        PlayerProgressProfile progress = saved.progressProfile();

        assertEquals(5, progress.skillPoints);
        assertEquals("BloodDash", progress.relicBindingsFor(0).get("primary"));
        assertEquals(1500L, progress.abilityCooldowns.get("BatForm"));
    }

    @Test
    void tryUnlockMutatesOnlySharedProgressSlice() {
        FakeRepository repository = new FakeRepository();
        UUID uuid = UUID.randomUUID();
        FakeProfile seeded = new FakeProfile();
        seeded.progress.skillPoints = 3;
        seeded.vampireOnlyCounter = 42;
        repository.storage.put(uuid, seeded);

        PlayerProgressStore<FakeProfile> store = new PlayerProgressStore<>(repository);

        assertTrue(store.tryUnlock(uuid, "mist-step", 2, java.util.List.of()));
        FakeProfile saved = repository.storage.get(uuid);

        assertEquals(1, saved.progress.skillPoints);
        assertTrue(saved.progress.unlockedSkills.contains("mist-step"));
        assertEquals(42, saved.vampireOnlyCounter);
    }

    private static final class FakeProfile implements PlayerProgressProfileHost {
        private PlayerProgressProfile progress = new PlayerProgressProfile();
        private int vampireOnlyCounter;

        @Override
        public PlayerProgressProfile progressProfile() {
            PlayerProgressProfile copy = new PlayerProgressProfile();
            copy.skillPoints = progress.skillPoints;
            copy.totalSpent = progress.totalSpent;
            copy.unlockedSkills.addAll(progress.unlockedSkills);
            copy.relicBindings = new LinkedHashMap<>(progress.relicBindings);
            copy.activeRelicPreset = progress.activeRelicPreset;
            copy.relicPresets = PlayerProgressProfile.sanitizeNestedStringMap(progress.relicPresets);
            copy.abilityCooldowns = new LinkedHashMap<>(progress.abilityCooldowns);
            return copy;
        }

        @Override
        public void applyProgressProfile(PlayerProgressProfile progress) {
            this.progress = new PlayerProgressProfile();
            this.progress.skillPoints = progress.skillPoints;
            this.progress.totalSpent = progress.totalSpent;
            this.progress.unlockedSkills.addAll(progress.unlockedSkills);
            this.progress.relicBindings = new LinkedHashMap<>(progress.relicBindings);
            this.progress.activeRelicPreset = progress.activeRelicPreset;
            this.progress.relicPresets = PlayerProgressProfile.sanitizeNestedStringMap(progress.relicPresets);
            this.progress.abilityCooldowns = new LinkedHashMap<>(progress.abilityCooldowns);
        }
    }

    private static final class FakeRepository implements PlayerProfileRepository<FakeProfile> {
        private final Map<UUID, FakeProfile> storage = new ConcurrentHashMap<>();

        @Override
        public FakeProfile load(UUID uuid) {
            return storage.computeIfAbsent(uuid, ignored -> new FakeProfile());
        }

        @Override
        public void save(UUID uuid, FakeProfile profile) {
            storage.put(uuid, profile);
        }
    }
}
