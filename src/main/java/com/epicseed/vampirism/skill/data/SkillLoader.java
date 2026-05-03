package com.epicseed.vampirism.skill.data;

import com.epicseed.epiccore.skill.data.SkillDataLoadHooks;
import com.epicseed.epiccore.skill.data.SkillLoaderModelAdapter;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.ModifierDef;
import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.model.StatDef;
import com.epicseed.epiccore.skill.model.StateDef;

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
}
