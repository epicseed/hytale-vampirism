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
import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.skill.registry.SkillRegistry;

public final class VampirismProgressionDefinitionProvider implements ProgressionDefinitionProvider {

    private static final VampirismProgressionDefinitionProvider INSTANCE = new VampirismProgressionDefinitionProvider();

    private VampirismProgressionDefinitionProvider() {
    }

    public static VampirismProgressionDefinitionProvider instance() {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public Collection<Skill> getAllSkills() {
        SkillRegistry skillRegistry = Vampirism.getInstance().GetSkillRegistry();
        return skillRegistry != null ? skillRegistry.GetAll() : List.of();
    }

    @Override
    @Nullable
    public Passive getPassive(String id) {
        return Vampirism.getInstance().GetPassiveRegistry().Get(id);
    }

    @Override
    @Nullable
    public Ability getAbility(String id) {
        return Vampirism.getInstance().GetAbilityRegistry().Get(id);
    }

    @Override
    @Nullable
    public Skill getSkill(String id) {
        SkillRegistry skillRegistry = Vampirism.getInstance().GetSkillRegistry();
        return skillRegistry != null ? skillRegistry.GetSkill(id) : null;
    }

    @Override
    @Nullable
    public EffectDef getEffect(String id) {
        return Vampirism.getInstance().GetEffectDefRegistry().Get(id);
    }
}
