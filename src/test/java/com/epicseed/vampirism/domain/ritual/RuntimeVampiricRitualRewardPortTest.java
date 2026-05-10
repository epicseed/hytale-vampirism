package com.epicseed.vampirism.domain.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.skill.runtime.TemporaryModifierTracker;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.domain.lineage.VampiricLineageRegistry;
import com.epicseed.vampirism.domain.lineage.VampiricLineageService;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatPolicy;
import com.epicseed.vampirism.domain.masquerade.MasqueradeHeatService;

class RuntimeVampiricRitualRewardPortTest {

    private TestProgressionAccess progressionAccess;

    @BeforeEach
    void setUp() {
        progressionAccess = new TestProgressionAccess();
    }

    @Test
    void applySideEffectDispatchesToRegisteredHandler() {
        UUID uuid = UUID.randomUUID();
        List<String> invocations = new ArrayList<>();
        LinkedHashMap<String, RuntimeVampiricRitualRewardPort.SideEffectHandler> handlers = new LinkedHashMap<>();
        handlers.put(
                VampiricRitualRegistry.SIDE_EFFECT_MARK_PREY,
                (playerId, ritualId) -> invocations.add(playerId + ":" + ritualId));

        RuntimeVampiricRitualRewardPort rewardPort = new RuntimeVampiricRitualRewardPort(
                progressionAccess,
                new VampiricLineageService(new VampiricLineageRegistry(), progressionAccess),
                new com.epicseed.vampirism.domain.hunt.NightHuntService(progressionAccess),
                new MasqueradeHeatService(MasqueradeHeatPolicy.defaults()),
                new TemporaryModifierTracker<StatType>(),
                null,
                handlers);

        rewardPort.applySideEffect(uuid, "ritual.mark_prey", "  mark_prey  ");

        assertEquals(List.of(uuid + ":ritual.mark_prey"), invocations);
    }

    @Test
    void applySideEffectIgnoresUnknownSideEffectIds() {
        List<String> invocations = new ArrayList<>();
        LinkedHashMap<String, RuntimeVampiricRitualRewardPort.SideEffectHandler> handlers = new LinkedHashMap<>();
        handlers.put(VampiricRitualRegistry.SIDE_EFFECT_MARK_PREY, (uuid, ritualId) -> invocations.add(ritualId));

        RuntimeVampiricRitualRewardPort rewardPort = new RuntimeVampiricRitualRewardPort(
                progressionAccess,
                new VampiricLineageService(new VampiricLineageRegistry(), progressionAccess),
                new com.epicseed.vampirism.domain.hunt.NightHuntService(progressionAccess),
                new MasqueradeHeatService(MasqueradeHeatPolicy.defaults()),
                new TemporaryModifierTracker<StatType>(),
                null,
                handlers);

        rewardPort.applySideEffect(UUID.randomUUID(), "ritual.unknown", "unknown_side_effect");

        assertEquals(List.of(), invocations);
    }

    @Test
    void summonFamiliarSideEffectDispatchesThroughDeferredDispatcher() {
        UUID uuid = UUID.randomUUID();
        List<String> invocations = new ArrayList<>();

        RuntimeVampiricRitualRewardPort rewardPort = new RuntimeVampiricRitualRewardPort(
                progressionAccess,
                new VampiricLineageService(new VampiricLineageRegistry(), progressionAccess),
                new com.epicseed.vampirism.domain.hunt.NightHuntService(progressionAccess),
                new MasqueradeHeatService(MasqueradeHeatPolicy.defaults()),
                new TemporaryModifierTracker<StatType>(),
                (playerId, action) -> {
                    invocations.add(playerId + ":scheduled");
                    return true;
                },
                null);

        rewardPort.applySideEffect(uuid, "ritual.summon_familiar", VampiricRitualRegistry.SIDE_EFFECT_SUMMON_FAMILIAR);

        assertEquals(List.of(uuid + ":scheduled"), invocations);
    }

    private static final class TestProgressionAccess implements VampirismSkillProgressionAccess {

        @Override
        public int getAcquiredSkillPoints(UUID uuid) {
            return 0;
        }

        @Override
        public void addSkillPoints(UUID uuid, int amount) {
        }

        @Override
        public void setSkillPoints(UUID uuid, int amount) {
        }

        @Override
        public int getSkillPoints(UUID uuid) {
            return 0;
        }

        @Override
        public boolean hasSkill(UUID uuid, String skillId) {
            return false;
        }

        @Override
        public boolean canUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds) {
            return false;
        }

        @Override
        public boolean tryUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds) {
            return false;
        }

        @Override
        public boolean grantSkill(UUID uuid, String skillId) {
            return false;
        }

        @Override
        public Set<String> getUnlockedSkillIds(UUID uuid) {
            return Set.of();
        }

        @Override
        public void resetSkills(UUID uuid) {
        }
    }
}
