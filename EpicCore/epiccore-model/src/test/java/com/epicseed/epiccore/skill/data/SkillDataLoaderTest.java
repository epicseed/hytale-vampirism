package com.epicseed.epiccore.skill.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.ModifierDef;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.model.StateDef;
import com.epicseed.epiccore.skill.model.StatDef;

class SkillDataLoaderTest {

    private static final SkillDataPaths VALID_PATHS =
            new SkillDataPaths("missing/skills", "missing/legacy", "skill-data/valid-skills.json");
    private static final SkillDataPaths INVALID_PATHS =
            new SkillDataPaths("missing/skills", "missing/legacy", "skill-data/invalid-skills.json");

    @Test
    void loadSkillsAndDefinitionsPopulateRegistriesAndHooks() {
        CapturingHooks hooks = new CapturingHooks();
        SkillDataLoader<Ability, ModifierDef, ReusableDef, StateDef, StatDef> loader =
                new SkillDataLoader<>(VALID_PATHS, new TestModelAdapter(), hooks);
        IdRegistry<Skill> skillRegistry = new IdRegistry<>(entry -> entry.id);
        SkillDefinitionRegistries<Ability, ModifierDef, ReusableDef, StateDef, StatDef> registries =
                new SkillDefinitionRegistries<>(
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id),
                        new IdRegistry<>(entry -> entry.id));

        List<Skill> skills = loader.loadSkills(skillRegistry);
        loader.loadDefinitions(registries);

        assertEquals(1, skills.size());
        Skill skill = skillRegistry.get("claw-skill");
        assertNotNull(skill);
        assertEquals("claw", skill.abilityId);
        assertSame(TestStat.ABILITY_POWER, skill.modifiers.get(0).stat);
        assertNotNull(registries.abilityRegistry().get("claw"));
        assertNotNull(registries.actionRegistry().get("deal-damage"));
        assertEquals("Potion_Claw", hooks.stateEffectBindings.get("FERAL"));
        assertEquals("claw", hooks.abilitySlotBindings.get("primary"));
    }

    @Test
    void validateSkillDataRejectsInvalidLegacyJson() {
        SkillDataLoader<Ability, ModifierDef, ReusableDef, StateDef, StatDef> loader =
                new SkillDataLoader<>(INVALID_PATHS, new TestModelAdapter(), SkillDataLoadHooks.noop());

        IllegalStateException error = assertThrows(IllegalStateException.class, loader::validateSkillData);

        assertTrue(error.getMessage().contains("position"));
        assertTrue(error.getMessage().contains("abilityId"));
    }

    private enum TestStat implements StatType {
        ABILITY_POWER
    }

    private static final class TestModelAdapter
            implements SkillLoaderModelAdapter<Ability, ModifierDef, ReusableDef, StateDef, StatDef> {

        @Override
        public Ability newAbility() {
            return new Ability();
        }

        @Override
        public ModifierDef newModifierDef() {
            return new ModifierDef();
        }

        @Override
        public ReusableDef newReusableDef() {
            return new ReusableDef();
        }

        @Override
        public StateDef newState() {
            return new StateDef();
        }

        @Override
        public StatDef newStatDef() {
            return new StatDef();
        }

        @Override
        public StatType resolveStatType(String statId) {
            if ("ABILITY_POWER".equals(statId)) {
                return TestStat.ABILITY_POWER;
            }
            return null;
        }
    }

    private static final class CapturingHooks implements SkillDataLoadHooks {

        private Map<String, String> stateEffectBindings;
        private Map<String, String> abilitySlotBindings;

        @Override
        public void applyStateEffectBindings(Map<String, String> stateEffectBindings) {
            this.stateEffectBindings = new LinkedHashMap<>(stateEffectBindings);
        }

        @Override
        public void applyAbilitySlotBindings(Map<String, String> abilitySlotBindings) {
            this.abilitySlotBindings = new LinkedHashMap<>(abilitySlotBindings);
        }
    }
}
