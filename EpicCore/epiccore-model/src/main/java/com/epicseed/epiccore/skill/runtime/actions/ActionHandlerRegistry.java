package com.epicseed.epiccore.skill.runtime.actions;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ActionHandlerRegistry<CTX> {
    private final Map<String, SkillActionHandler<CTX>> handlers = new LinkedHashMap<>();

    public ActionHandlerRegistry<CTX> register(String typeId, SkillActionHandler<CTX> handler) {
        handlers.put(typeId, handler);
        return this;
    }

    public SkillActionHandler<CTX> find(String typeId) {
        return handlers.get(typeId);
    }

    public Map<String, SkillActionHandler<CTX>> handlers() {
        return Collections.unmodifiableMap(handlers);
    }
}
