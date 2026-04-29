package com.epicseed.vampirism.skill.runtime.actions;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ActionHandlerRegistryTest {

    @Test
    void registerReturnsRegistryForChainingAndFindsHandler() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry();
        SkillActionHandler handler = (action, ctx) -> true;

        ActionHandlerRegistry result = registry.register("test", handler);

        assertSame(registry, result);
        assertSame(handler, registry.find("test"));
        assertNull(registry.find("missing"));
    }

    @Test
    void handlersViewIsImmutable() {
        ActionHandlerRegistry registry = new ActionHandlerRegistry();
        SkillActionHandler handler = (action, ctx) -> true;
        registry.register("test", handler);

        assertThrows(UnsupportedOperationException.class, () -> registry.handlers().put("other", handler));
    }
}
