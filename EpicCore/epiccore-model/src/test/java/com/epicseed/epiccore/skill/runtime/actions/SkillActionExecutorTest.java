package com.epicseed.epiccore.skill.runtime.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions;

class SkillActionExecutorTest {

    @Test
    void executeAllRunsRegisteredActions() {
        SkillRuntimeDefinitions.init(null);
        AtomicInteger executed = new AtomicInteger();
        ActionHandlerRegistry<String> registry = new ActionHandlerRegistry<String>()
                .register("apply", (action, ctx) -> {
                    executed.incrementAndGet();
                    return "value".equals(action.get("payload")) && "ctx".equals(ctx);
                });
        SkillActionExecutor<String> executor = new SkillActionExecutor<>(registry, (conditions, ctx) -> true);

        boolean result = executor.executeAll(
                List.of(Map.of("type", "apply", "payload", "value")),
                "ctx");

        assertTrue(result);
        assertEquals(1, executed.get());
    }

    @Test
    void executeSkipsHandlerWhenConditionsFail() {
        SkillRuntimeDefinitions.init(null);
        AtomicInteger executed = new AtomicInteger();
        ActionHandlerRegistry<String> registry = new ActionHandlerRegistry<String>()
                .register("apply", (action, ctx) -> {
                    executed.incrementAndGet();
                    return true;
                });
        SkillActionExecutor<String> executor = new SkillActionExecutor<>(
                registry,
                (conditions, ctx) -> conditions == null || conditions.isEmpty());

        boolean result = executor.execute(
                Map.of(
                        "type", "apply",
                        "conditions", List.of(Map.of("type", "blocked"))),
                "ctx");

        assertFalse(result);
        assertEquals(0, executed.get());
    }
}
