package com.epicseed.vampirism.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import org.junit.jupiter.api.Test;

class RegistryBackedAbilityDefinitionProviderTest {

    @Test
    void resolvesAbilitySkillAndEffectFromRegistries() {
        Ability ability = new Ability();
        ability.id = "mist-step";

        Skill skill = new Skill();
        skill.id = "mist-step-skill";

        EffectDef effect = new EffectDef();
        effect.id = "mist-step-effect";

        AbilityRegistry abilityRegistry = new AbilityRegistry();
        abilityRegistry.Register(ability);

        SkillRegistry skillRegistry = new SkillRegistry();
        skillRegistry.Register(skill);

        EffectDefRegistry effectRegistry = new EffectDefRegistry();
        effectRegistry.Register(effect);

        RegistryBackedAbilityDefinitionProvider provider =
                new RegistryBackedAbilityDefinitionProvider(abilityRegistry, skillRegistry, effectRegistry);

        assertSame(ability, provider.getAbility("mist-step"));
        assertSame(skill, provider.getSkill("mist-step-skill"));
        assertSame(effect, provider.getEffect("mist-step-effect"));
        assertNull(provider.getAbility("missing"));
    }
}
