package com.epicseed.vampirism.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VampirismProgressionDefinitionProviderTest {

    @AfterEach
    void tearDown() {
        VampirismProgressionDefinitionProvider.resetForTests();
    }

    @Test
    void returnsEmptyDefaultsWhenUninitialized() {
        VampirismProgressionDefinitionProvider provider = VampirismProgressionDefinitionProvider.instance();

        assertTrue(provider.getAllSkills().isEmpty());
        assertTrue(provider.getAllEffects().isEmpty());
        assertNull(provider.getSkill("missing"));
        assertNull(provider.getPassive("missing"));
        assertNull(provider.getAbility("missing"));
        assertNull(provider.getEffect("missing"));
    }

    @Test
    void resolvesConfiguredDefinitionsFromRegistries() {
        Skill skill = new Skill();
        skill.id = "blood-surge";

        Passive passive = new Passive();
        passive.id = "night-vision";

        Ability ability = new Ability();
        ability.id = "mist-step";

        EffectDef effect = new EffectDef();
        effect.id = "mist-form";

        SkillRegistry skillRegistry = new SkillRegistry();
        skillRegistry.Register(skill);

        PassiveRegistry passiveRegistry = new PassiveRegistry();
        passiveRegistry.Register(passive);

        AbilityRegistry abilityRegistry = new AbilityRegistry();
        abilityRegistry.Register(ability);

        EffectDefRegistry effectDefRegistry = new EffectDefRegistry();
        effectDefRegistry.Register(effect);

        VampirismProgressionDefinitionProvider.init(
                skillRegistry,
                passiveRegistry,
                abilityRegistry,
                effectDefRegistry);

        VampirismProgressionDefinitionProvider provider = VampirismProgressionDefinitionProvider.instance();
        assertSame(skill, provider.getSkill("blood-surge"));
        assertSame(passive, provider.getPassive("night-vision"));
        assertSame(ability, provider.getAbility("mist-step"));
        assertSame(effect, provider.getEffect("mist-form"));
        assertEquals(1, provider.getAllSkills().size());
        assertEquals(1, provider.getAllEffects().size());
    }
}
