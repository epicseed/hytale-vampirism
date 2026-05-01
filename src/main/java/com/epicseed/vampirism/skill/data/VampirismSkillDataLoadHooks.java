package com.epicseed.vampirism.skill.data;

import com.epicseed.epiccore.skill.data.SkillDataLoadHooks;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeBindings;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeBindingsHolder;

import java.util.Objects;

public final class VampirismSkillDataLoadHooks implements SkillDataLoadHooks {

    private final SkillRuntimeBindingsHolder runtimeBindings;

    public VampirismSkillDataLoadHooks(SkillRuntimeBindingsHolder runtimeBindings) {
        this.runtimeBindings = Objects.requireNonNull(runtimeBindings, "runtimeBindings");
    }

    @Override
    public void applyRuntimeBindings(SkillRuntimeBindings runtimeBindings) {
        this.runtimeBindings.replace(runtimeBindings);
    }
}
