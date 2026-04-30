package com.epicseed.vampirism.skill.runtime.actions;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;

public final class ActionHandlerRegistry extends com.epicseed.epiccore.skill.runtime.actions.ActionHandlerRegistry<SkillRuntimeContext> {

    @Override
    public ActionHandlerRegistry register(@Nonnull String typeId,
                                          @Nonnull com.epicseed.epiccore.skill.runtime.actions.SkillActionHandler<SkillRuntimeContext> handler) {
        super.register(typeId, handler);
        return this;
    }
}
