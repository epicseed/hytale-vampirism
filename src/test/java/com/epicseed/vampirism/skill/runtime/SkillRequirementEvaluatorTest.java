package com.epicseed.vampirism.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.SkillRegistry;

class SkillRequirementEvaluatorTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");
    private static final String SKILL_ID = "night-strike-skill";
    private static final String ABILITY_ID = "night-strike";
    private static final String PASSIVE_ID = "blood-sense";

    @BeforeEach
    void setUp() {
        Skill skill = new Skill();
        skill.id = SKILL_ID;
        skill.abilityId = ABILITY_ID;
        skill.passiveId = PASSIVE_ID;

        SkillRegistry skillRegistry = new SkillRegistry();
        skillRegistry.Register(skill);
        VampirismProgressionDefinitionProvider.init(skillRegistry, null, null, null);
        SkillRequirementEvaluator.resetForTests();
    }

    @AfterEach
    void tearDown() {
        SkillRequirementEvaluator.resetForTests();
        VampirismProgressionDefinitionProvider.resetForTests();
    }

    @Test
    void evaluateBeforeRuntimeInitFallsBackToUninitializedProvider() {
        SkillRuntimeContext ctx = new SkillRuntimeContext(PLAYER_ID, null, null);

        assertFalse(SkillRequirementEvaluator.evaluate(Map.of(
                "type", "abilityUnlocked",
                "abilityId", ABILITY_ID), ctx));
        assertFalse(SkillRequirementEvaluator.evaluate(Map.of(
                "type", "passiveUnlocked",
                "passiveId", PASSIVE_ID), ctx));
    }

    @Test
    void initRefreshesUnlockedSkillAccessForRuntimeChecks() {
        SkillRuntimeContext ctx = new SkillRuntimeContext(PLAYER_ID, null, null);

        PlayerRegistrySkillProgressionAccess.init(backendWithUnlockedSkills(Set.of()));
        SkillRequirementEvaluator.init(PlayerRegistrySkillProgressionAccess.instance());
        assertFalse(SkillRequirementEvaluator.evaluate(Map.of(
                "type", "abilityUnlocked",
                "abilityId", ABILITY_ID), ctx));

        PlayerRegistrySkillProgressionAccess.init(backendWithUnlockedSkills(Set.of(SKILL_ID)));
        SkillRequirementEvaluator.init(PlayerRegistrySkillProgressionAccess.instance());
        assertTrue(SkillRequirementEvaluator.evaluate(Map.of(
                "type", "abilityUnlocked",
                "abilityId", ABILITY_ID), ctx));
        assertTrue(SkillRequirementEvaluator.evaluate(Map.of(
                "type", "passiveUnlocked",
                "passiveId", PASSIVE_ID), ctx));
    }

    private static PlayerRegistrySkillProgressionAccess.Backend backendWithUnlockedSkills(Set<String> unlockedSkillIds) {
        return new PlayerRegistrySkillProgressionAccess.Backend() {
            @Override
            public int getSkillPoints(UUID uuid) {
                return 0;
            }

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
            public boolean hasSkill(UUID uuid, String skillId) {
                return unlockedSkillIds.contains(skillId);
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
                return unlockedSkillIds;
            }

            @Override
            public void resetSkills(UUID uuid) {
            }
        };
    }
}
