package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;

public final class ActionHandlerRegistry {
    private final Map<String, SkillActionHandler> handlers = new LinkedHashMap<>();

    public ActionHandlerRegistry register(@Nonnull String typeId, @Nonnull SkillActionHandler handler) {
        handlers.put(typeId, handler);
        return this;
    }

    public SkillActionHandler find(@Nonnull String typeId) {
        return handlers.get(typeId);
    }

    public Map<String, SkillActionHandler> handlers() {
        return Collections.unmodifiableMap(handlers);
    }
}
