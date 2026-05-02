package com.epicseed.vampirism.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PlayerRegistrySkillProgressionAccessTest {

    @AfterEach
    void tearDown() {
        PlayerRegistrySkillProgressionAccess.resetForTests();
    }

    @Test
    void delegatesProgressionOperationsToConfiguredBackend() {
        UUID uuid = UUID.randomUUID();
        FakeBackend backend = new FakeBackend();
        PlayerRegistrySkillProgressionAccess.init(backend);

        PlayerRegistrySkillProgressionAccess access = PlayerRegistrySkillProgressionAccess.instance();
        access.addSkillPoints(uuid, 7);

        assertEquals(7, access.getSkillPoints(uuid));
        assertEquals(7, access.getAcquiredSkillPoints(uuid));
        assertFalse(access.hasSkill(uuid, "BatForm"));

        assertTrue(access.grantSkill(uuid, "BatForm"));
        assertTrue(access.hasSkill(uuid, "BatForm"));
        assertEquals(Set.of("BatForm"), access.getUnlockedSkillIds(uuid));

        access.resetSkills(uuid);

        assertEquals(Set.of(), access.getUnlockedSkillIds(uuid));
        assertFalse(access.hasSkill(uuid, "BatForm"));
    }

    private static final class FakeBackend implements PlayerRegistrySkillProgressionAccess.Backend {
        private int skillPoints;
        private int acquiredSkillPoints;
        private final Set<String> unlockedSkillIds = new LinkedHashSet<>();

        @Override
        public int getSkillPoints(UUID uuid) {
            return skillPoints;
        }

        @Override
        public int getAcquiredSkillPoints(UUID uuid) {
            return acquiredSkillPoints;
        }

        @Override
        public void addSkillPoints(UUID uuid, int amount) {
            skillPoints += amount;
            acquiredSkillPoints += amount;
        }

        @Override
        public void setSkillPoints(UUID uuid, int amount) {
            skillPoints = amount;
        }

        @Override
        public boolean hasSkill(UUID uuid, String skillId) {
            return unlockedSkillIds.contains(skillId);
        }

        @Override
        public boolean canUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds) {
            return !unlockedSkillIds.contains(skillId) && skillPoints >= cost;
        }

        @Override
        public boolean tryUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds) {
            if (!canUnlock(uuid, skillId, cost, requirementIds)) {
                return false;
            }
            skillPoints -= cost;
            unlockedSkillIds.add(skillId);
            return true;
        }

        @Override
        public boolean grantSkill(UUID uuid, String skillId) {
            return unlockedSkillIds.add(skillId);
        }

        @Override
        public Set<String> getUnlockedSkillIds(UUID uuid) {
            return Set.copyOf(unlockedSkillIds);
        }

        @Override
        public void resetSkills(UUID uuid) {
            unlockedSkillIds.clear();
        }
    }
}
