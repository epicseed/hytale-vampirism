package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.AbilityDefinitionProvider;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;

public final class RegistryBackedAbilityDefinitionProvider implements AbilityDefinitionProvider {

    private final AbilityRegistry abilityRegistry;
    private final SkillRegistry skillRegistry;
    private final EffectDefRegistry effectDefRegistry;

    public RegistryBackedAbilityDefinitionProvider(
            AbilityRegistry abilityRegistry,
            SkillRegistry skillRegistry,
            EffectDefRegistry effectDefRegistry) {
        this.abilityRegistry = abilityRegistry;
        this.skillRegistry = skillRegistry;
        this.effectDefRegistry = effectDefRegistry;
    }

    @Override
    public Ability getAbility(String id) {
        return abilityRegistry != null ? abilityRegistry.Get(id) : null;
    }

    @Override
    public Skill getSkill(String id) {
        return skillRegistry != null ? skillRegistry.GetSkill(id) : null;
    }

    @Override
    public EffectDef getEffect(String id) {
        return effectDefRegistry != null ? effectDefRegistry.Get(id) : null;
    }
}
