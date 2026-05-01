package com.epicseed.vampirism.skill.runtime;

import java.util.Collection;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.runtime.AbilityDefinitionProvider;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;

public final class VampirismProgressionDefinitionProvider implements ProgressionDefinitionProvider, AbilityDefinitionProvider {

    private static final VampirismProgressionDefinitionProvider INSTANCE = new VampirismProgressionDefinitionProvider();

    @Nullable
    private volatile SkillRegistry skillRegistry;
    @Nullable
    private volatile PassiveRegistry passiveRegistry;
    @Nullable
    private volatile AbilityRegistry abilityRegistry;
    @Nullable
    private volatile EffectDefRegistry effectDefRegistry;

    private VampirismProgressionDefinitionProvider() {
    }

    public static VampirismProgressionDefinitionProvider instance() {
        return INSTANCE;
    }

    public static void init(@Nullable SkillRegistry skillRegistry,
                            @Nullable PassiveRegistry passiveRegistry,
                            @Nullable AbilityRegistry abilityRegistry,
                            @Nullable EffectDefRegistry effectDefRegistry) {
        INSTANCE.skillRegistry = skillRegistry;
        INSTANCE.passiveRegistry = passiveRegistry;
        INSTANCE.abilityRegistry = abilityRegistry;
        INSTANCE.effectDefRegistry = effectDefRegistry;
    }

    static void resetForTests() {
        init(null, null, null, null);
    }

    @Override
    @Nonnull
    public Collection<Skill> getAllSkills() {
        return skillRegistry != null ? skillRegistry.GetAll() : List.of();
    }

    @Override
    @Nullable
    public Passive getPassive(String id) {
        return passiveRegistry != null ? passiveRegistry.Get(id) : null;
    }

    @Override
    @Nullable
    public Ability getAbility(String id) {
        return abilityRegistry != null ? abilityRegistry.Get(id) : null;
    }

    @Override
    @Nullable
    public Skill getSkill(String id) {
        return skillRegistry != null ? skillRegistry.GetSkill(id) : null;
    }

    @Override
    @Nullable
    public EffectDef getEffect(String id) {
        return effectDefRegistry != null ? effectDefRegistry.Get(id) : null;
    }

    @Nonnull
    public Collection<EffectDef> getAllEffects() {
        return effectDefRegistry != null ? effectDefRegistry.GetAll() : List.of();
    }
}
