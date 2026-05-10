package com.epicseed.vampirism.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.player.PlayerProfileRepository;
import com.epicseed.epiccore.player.PlayerProgressStore;
import com.epicseed.epiccore.vampirism.domain.player.PlayerVampireProfile;
import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.domain.progression.VampiricProgressionProofs;
import com.epicseed.vampirism.domain.ritual.VampiricRitualCompletionResult;
import com.epicseed.vampirism.domain.ritual.VampiricRitualContext;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRewardPort;
import com.epicseed.vampirism.domain.ritual.VampiricRitualService;

class HuntCompendiumNextRiteResolverTest {

    private VampiricRitualService ritualService;
    private HuntCompendiumNextRiteResolver resolver;

    @BeforeEach
    void setUp() {
        PlayerProgressStore<PlayerVampireProfile> progressStore = new PlayerProgressStore<>(new InMemoryRepository());
        VampirePlayerStateStore.init(progressStore);
        ritualService = new VampiricRitualService(VampiricRitualRegistry.defaultAscensionRegistry(), new NoOpRewardPort());
        resolver = new HuntCompendiumNextRiteResolver(ritualService);
    }

    @Test
    void resolvePrioritizesVeilAndExplainsMissingProof() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().setAgeTierId(uuid, "fledgling");

        HuntCompendiumNextRiteResolver.NextRite nextRite = resolver.resolve(
                uuid,
                new VampiricRitualContext(
                        uuid,
                        36,
                        1,
                        "fledgling",
                        Set.of("BloodThirst"),
                        Set.of(VampiricRitualRegistry.TAG_NIGHT)));

        assertNotNull(nextRite);
        assertEquals("Veil of Night", nextRite.ritualName());
        assertTrue(nextRite.guidance().contains("Complete a night hunt first."));
    }

    @Test
    void resolvePointsToAffinityGateAfterVeilIsComplete() {
        UUID uuid = UUID.randomUUID();
        VampirePlayerStateStore.get().setAgeTierId(uuid, "fledgling");
        VampiricRitualContext veilContext = new VampiricRitualContext(
                uuid,
                36,
                1,
                "fledgling",
                Set.of("BloodThirst"),
                Set.of(VampiricProgressionProofs.FIRST_NIGHT_HUNT_COMPLETION),
                Set.of(VampiricRitualRegistry.TAG_NIGHT));
        ritualService.syncAvailability(uuid, VampiricRitualRegistry.VEIL_OF_NIGHT_RITUAL_ID, veilContext);
        assertTrue(ritualService.begin(uuid, VampiricRitualRegistry.VEIL_OF_NIGHT_RITUAL_ID, veilContext, 5_000L));
        VampiricRitualCompletionResult result =
                ritualService.tryComplete(uuid, VampiricRitualRegistry.VEIL_OF_NIGHT_RITUAL_ID, veilContext, 6_000L);
        assertTrue(result.completed());

        HuntCompendiumNextRiteResolver.NextRite nextRite = resolver.resolve(
                uuid,
                new VampiricRitualContext(
                        uuid,
                        42,
                        2,
                        "fledgling",
                        Set.of("BloodThirst", "BloodThrow"),
                        Set.of(VampiricProgressionProofs.FIRST_NIGHT_HUNT_COMPLETION),
                        Map.of("vermin", 0),
                        Set.of(VampiricRitualRegistry.TAG_NIGHT)));

        assertNotNull(nextRite);
        assertEquals("Summon Familiar", nextRite.ritualName());
        assertEquals("Earn Vermin 1+ affinity from night hunt rewards (currently 0/1).", nextRite.guidance());
    }

    private static final class NoOpRewardPort implements VampiricRitualRewardPort {
        @Override
        public void grantSkill(UUID uuid, String skillId) {
        }

        @Override
        public void addSkillPoints(UUID uuid, int amount) {
        }

        @Override
        public void adjustBlood(UUID uuid, int delta) {
        }

        @Override
        public void setAgeTier(UUID uuid, String ageTierId) {
            VampirePlayerStateStore.get().setAgeTierId(uuid, ageTierId);
        }

        @Override
        public void setLineage(UUID uuid, String lineageId) {
        }

        @Override
        public void applySideEffect(UUID uuid, String ritualId, String sideEffectId) {
        }
    }

    private static final class InMemoryRepository implements PlayerProfileRepository<PlayerVampireProfile> {
        private final Map<UUID, PlayerVampireProfile> storage = new HashMap<>();

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
