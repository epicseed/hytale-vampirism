package com.epicseed.vampirism.skill.data;

import java.util.List;

import com.epicseed.epiccore.skill.data.SkillDataLoadHooks;
import com.epicseed.epiccore.skill.data.SkillDefinitionRegistries;
import com.epicseed.epiccore.skill.data.SkillLoaderModelAdapter;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.ModifierDef;
import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.model.StatDef;
import com.epicseed.epiccore.skill.model.StateDef;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.ModifierDefRegistry;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.ReusableDefRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.registry.StateRegistry;
import com.epicseed.vampirism.skill.registry.StatDefRegistry;

public class SkillLoader extends com.epicseed.epiccore.skill.data.SkillDataLoader<Ability, ModifierDef, ReusableDef, StateDef, StatDef> {

    public SkillLoader() {
        this(SkillDataPaths.vampirismDefaults(), new VampirismSkillLoaderModelAdapter(), SkillDataLoadHooks.noop());
    }

    public SkillLoader(com.epicseed.epiccore.skill.data.SkillDataPaths dataPaths) {
        this(dataPaths, new VampirismSkillLoaderModelAdapter(), SkillDataLoadHooks.noop());
    }

    public SkillLoader(com.epicseed.epiccore.skill.data.SkillDataPaths dataPaths, SkillDataLoadHooks loadHooks) {
        this(dataPaths, new VampirismSkillLoaderModelAdapter(), loadHooks);
    }

    public SkillLoader(com.epicseed.epiccore.skill.data.SkillDataPaths dataPaths,
                       SkillLoaderModelAdapter<Ability, ModifierDef, ReusableDef, StateDef, StatDef> modelAdapter,
                       SkillDataLoadHooks loadHooks) {
        super(dataPaths, modelAdapter != null ? modelAdapter : new VampirismSkillLoaderModelAdapter(), loadHooks);
    }

    public List<Skill> LoadSkills(SkillRegistry registry) {
        return loadSkills(registry);
    }

    public void ValidateSkillData() {
        validateSkillData();
    }

    public void LoadDefinitions(PassiveRegistry passiveRegistry,
                                AbilityRegistry abilityRegistry,
                                ModifierDefRegistry modifierDefRegistry,
                                EffectDefRegistry effectDefRegistry,
                                StateRegistry stateRegistry,
                                StatDefRegistry statDefRegistry,
                                ReusableDefRegistry conditionRegistry,
                                ReusableDefRegistry requirementRegistry,
                                ReusableDefRegistry triggerRegistry,
                                ReusableDefRegistry actionRegistry,
                                ReusableDefRegistry targetingRegistry) {
        loadDefinitions(new SkillDefinitionRegistries<>(
                passiveRegistry,
                abilityRegistry,
                modifierDefRegistry,
                effectDefRegistry,
                stateRegistry,
                statDefRegistry,
                conditionRegistry,
                requirementRegistry,
                triggerRegistry,
                actionRegistry,
                targetingRegistry));
    }
}
