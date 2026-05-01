package com.epicseed.vampirism.skill.runtime.actions;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.epicseed.epiccore.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.epiccore.skill.runtime.actions.SkillActionHandler;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import org.junit.jupiter.api.Test;

class ActionHandlerRegistryTest {

    @Test
    void registerReturnsRegistryForChainingAndFindsHandler() {
        ActionHandlerRegistry<SkillRuntimeContext> registry = new ActionHandlerRegistry<>();
        SkillActionHandler<SkillRuntimeContext> handler = (action, ctx) -> true;

        ActionHandlerRegistry<SkillRuntimeContext> result = registry.register("test", handler);

        assertSame(registry, result);
        assertSame(handler, registry.find("test"));
        assertNull(registry.find("missing"));
    }

    @Test
    void handlersViewIsImmutable() {
        ActionHandlerRegistry<SkillRuntimeContext> registry = new ActionHandlerRegistry<>();
        SkillActionHandler<SkillRuntimeContext> handler = (action, ctx) -> true;
        registry.register("test", handler);

        assertThrows(UnsupportedOperationException.class, () -> registry.handlers().put("other", handler));
    }
}
