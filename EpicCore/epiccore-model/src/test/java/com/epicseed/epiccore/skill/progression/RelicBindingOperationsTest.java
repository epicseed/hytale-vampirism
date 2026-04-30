package com.epicseed.epiccore.skill.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;
import org.junit.jupiter.api.Test;

class RelicBindingOperationsTest {

    @Test
    void effectiveBindingsPreferOverridesAndHonorBlankOptOut() {
        FakeRelicBindingDefaults defaults = new FakeRelicBindingDefaults(Map.of(
                "primary", "slash",
                "secondary", "parry",
                "ability1", "mist-step"));

        LinkedHashMap<String, String> effective = RelicBindingOperations.getEffectiveBindings(
                Map.of("primary", "", "secondary", "riposte"),
                defaults);

        assertFalse(effective.containsKey("primary"));
        assertEquals("riposte", effective.get("secondary"));
        assertEquals("mist-step", effective.get("ability1"));
    }

    @Test
    void validatePendingBindingsBlocksSlotsLockedByCooldown() {
        UUID uuid = UUID.randomUUID();
        FakeRelicBindingDefaults defaults = new FakeRelicBindingDefaults(Map.of("primary", "slash"));
        FakeAbilityCooldownAccess cooldowns = new FakeAbilityCooldownAccess(Map.of("slash", 1500L));

        String message = RelicBindingOperations.validatePendingBindings(
                uuid,
                Map.of("primary", "slash"),
                Map.of("primary", "mist-step"),
                defaults,
                cooldowns,
                id -> id);

        assertTrue(message.contains("slash"));
        assertTrue(message.contains("2s remaining"));
    }

    @Test
    void listBindableAbilitySkillsOnlyReturnsUnlockedAbilitySkills() {
        UUID uuid = UUID.randomUUID();
        FakeDefinitions definitions = new FakeDefinitions();
        FakeSkillProgressionAccess access = new FakeSkillProgressionAccess();

        Skill unlockedAbility = skill("mist-step-skill", "mist-step");
        Skill lockedAbility = skill("blood-burst-skill", "blood-burst");
        Skill passiveOnly = skill("night-eyes-skill", null);
        definitions.skills = List.of(unlockedAbility, lockedAbility, passiveOnly);
        access.unlocked.add("mist-step-skill");
        access.unlocked.add("night-eyes-skill");

        List<Skill> bindable = RelicBindingOperations.listBindableAbilitySkills(uuid, definitions, access);

        assertEquals(List.of(unlockedAbility), bindable);
    }

    @Test
    void abilityLabelFallsBackToOwnerThenId() {
        FakeDefinitions definitions = new FakeDefinitions();
        Ability ability = new Ability();
        ability.id = "mist-step";
        ability.displayName = "";
        definitions.abilities.put("mist-step", ability);

        Skill owner = skill("mist-step-skill", "mist-step");
        owner.displayName = "Mist Step";

        assertEquals("Mist Step", RelicBindingOperations.abilityLabel("mist-step", owner, definitions));
        assertEquals("unknown", RelicBindingOperations.abilityLabel("unknown", definitions));
    }

    @Test
    void helperMethodsKeepExistingPresetAndCooldownBehavior() {
        assertFalse(RelicBindingOperations.normalized(" ").isPresent());
        assertEquals("1", RelicBindingOperations.formatCooldown(1));
        assertEquals("No Offhand", RelicBindingOperations.presetLabel(4, 4));
        assertEquals(4, RelicBindingOperations.clampPresetIndex(7, 5));
        assertEquals(-1, RelicBindingOperations.presetIndexForUtilitySelection(9, -1, 4));
        assertNull(RelicBindingOperations.normalized(null).orElse(null));
    }

    private static Skill skill(String id, String abilityId) {
        Skill skill = new Skill();
        skill.id = id;
        skill.displayName = id;
        skill.abilityId = abilityId;
        return skill;
    }

    private record FakeRelicBindingDefaults(Map<String, String> defaults) implements RelicBindingDefaults {

        @Override
        public List<String> slotKeys() {
            return List.copyOf(defaults.keySet());
        }

        @Override
        public String defaultAbilityId(String slot) {
            return defaults.get(slot);
        }
    }

    private record FakeAbilityCooldownAccess(Map<String, Long> cooldowns) implements AbilityCooldownAccess {

        @Override
        public long remainingMs(UUID uuid, String abilityId) {
            return cooldowns.getOrDefault(abilityId, 0L);
        }
    }

    private static final class FakeSkillProgressionAccess implements SkillProgressionAccess {
        private final java.util.Set<String> unlocked = new java.util.HashSet<>();

        @Override
        public int getSkillPoints(UUID uuid) {
            return 0;
        }

        @Override
        public boolean hasSkill(UUID uuid, String skillId) {
            return unlocked.contains(skillId);
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
        public java.util.Set<String> getUnlockedSkillIds(UUID uuid) {
            return java.util.Set.copyOf(unlocked);
        }

        @Override
        public void resetSkills(UUID uuid) {
        }
    }

    private static final class FakeDefinitions implements ProgressionDefinitionProvider {
        private final Map<String, Ability> abilities = new LinkedHashMap<>();
        private List<Skill> skills = List.of();

        @Override
        public Collection<Skill> getAllSkills() {
            return skills;
        }

        @Override
        public Passive getPassive(String id) {
            return null;
        }

        @Override
        public Ability getAbility(String id) {
            return abilities.get(id);
        }

        @Override
        public Skill getSkill(String id) {
            return skills.stream().filter(skill -> id.equals(skill.id)).findFirst().orElse(null);
        }

        @Override
        public EffectDef getEffect(String id) {
            return null;
        }
    }
}
