package com.epicseed.epiccore.skill.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.InlineModifier;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;
import org.junit.jupiter.api.Test;

class SkillTreeOperationsTest {

    @Test
    void stateForMarksSkillUnlockableWhenRequirementsAndPointsAreAvailable() {
        UUID uuid = UUID.randomUUID();
        FakeSkillProgressionAccess access = new FakeSkillProgressionAccess(3);
        access.unlocked.add("root");

        Skill root = skill("root", 1);
        Skill child = skill("child", 2);
        child.requires = java.util.List.of(root);

        SkillNodeState state = SkillTreeOperations.stateFor(child, uuid, access);

        assertTrue(state.canUnlock());
        assertTrue(state.depsMet());
        assertEquals("Unlock (2 points)", state.unlockStatus());
        assertEquals("#ffffff", state.indicatorColor());
    }

    @Test
    void unlockConsumesPointsAndMarksSkillUnlocked() {
        UUID uuid = UUID.randomUUID();
        FakeSkillProgressionAccess access = new FakeSkillProgressionAccess(3);
        Skill skill = skill("mist-step", 2);

        SkillUnlockResult result = SkillTreeOperations.unlock(uuid, skill, access);

        assertTrue(result.unlocked());
        assertEquals(1, access.skillPoints);
        assertTrue(access.unlocked.contains("mist-step"));
    }

    @Test
    void evaluateUnlockReportsMissingRequirements() {
        UUID uuid = UUID.randomUUID();
        FakeSkillProgressionAccess access = new FakeSkillProgressionAccess(5);

        Skill requirement = skill("root", 1);
        Skill child = skill("child", 2);
        child.requires = java.util.List.of(requirement);

        SkillUnlockResult result = SkillTreeOperations.evaluateUnlock(uuid, child, access);

        assertEquals(SkillUnlockStatus.MISSING_REQUIREMENTS, result.status());
        assertFalse(result.canUnlock());
    }

    @Test
    void buildDescriptionUsesAbilityAndPassiveMetadata() {
        FakeDefinitions definitions = new FakeDefinitions();

        Ability ability = new Ability();
        ability.id = "mist-step";
        ability.bloodCost = 12;
        ability.cooldown = 1.5f;
        definitions.abilities.put(ability.id, ability);

        Passive passive = new Passive();
        passive.id = "night-eyes";
        passive.modifiers = java.util.List.of(new InlineModifier(), new InlineModifier());
        definitions.passives.put(passive.id, passive);

        Skill activeSkill = skill("mist-step-skill", 1);
        activeSkill.description = "Teleport behind the target.";
        activeSkill.abilityId = "mist-step";

        Skill passiveSkill = skill("night-eyes-skill", 1);
        passiveSkill.description = "See in the dark.";
        passiveSkill.passiveId = "night-eyes";

        String activeDescription = SkillTreeOperations.buildDescription(activeSkill, definitions);
        String passiveDescription = SkillTreeOperations.buildDescription(passiveSkill, definitions);

        assertTrue(activeDescription.contains("[Active]"));
        assertTrue(activeDescription.contains("Blood: 12"));
        assertTrue(activeDescription.contains("Cooldown: 1.5s"));
        assertTrue(passiveDescription.contains("[Passive]"));
        assertTrue(passiveDescription.contains("Modifiers: 2"));
    }

    private static Skill skill(String id, int cost) {
        Skill skill = new Skill();
        skill.id = id;
        skill.displayName = id;
        skill.cost = cost;
        return skill;
    }

    private static final class FakeSkillProgressionAccess implements SkillProgressionAccess {
        private final Set<String> unlocked = new HashSet<>();
        private int skillPoints;

        private FakeSkillProgressionAccess(int skillPoints) {
            this.skillPoints = skillPoints;
        }

        @Override
        public int getSkillPoints(UUID uuid) {
            return skillPoints;
        }

        @Override
        public boolean hasSkill(UUID uuid, String skillId) {
            return unlocked.contains(skillId);
        }

        @Override
        public boolean canUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds) {
            if (unlocked.contains(skillId) || skillPoints < cost) {
                return false;
            }
            for (String requirementId : requirementIds) {
                if (!unlocked.contains(requirementId)) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean tryUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds) {
            if (!canUnlock(uuid, skillId, cost, requirementIds)) {
                return false;
            }
            skillPoints -= cost;
            unlocked.add(skillId);
            return true;
        }

        @Override
        public boolean grantSkill(UUID uuid, String skillId) {
            return unlocked.add(skillId);
        }

        @Override
        public Set<String> getUnlockedSkillIds(UUID uuid) {
            return Set.copyOf(unlocked);
        }

        @Override
        public void resetSkills(UUID uuid) {
            unlocked.clear();
        }
    }

    private static final class FakeDefinitions implements ProgressionDefinitionProvider {
        private final Map<String, Ability> abilities = new HashMap<>();
        private final Map<String, Passive> passives = new HashMap<>();
        private final Map<String, Skill> skills = new HashMap<>();

        @Override
        public Collection<Skill> getAllSkills() {
            return skills.values();
        }

        @Override
        public Passive getPassive(String id) {
            return passives.get(id);
        }

        @Override
        public Ability getAbility(String id) {
            return abilities.get(id);
        }

        @Override
        public Skill getSkill(String id) {
            return skills.get(id);
        }

        @Override
        public EffectDef getEffect(String id) {
            return null;
        }
    }
}
