package com.epicseed.epiccore.skill.runtime.actions;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions;

public final class SkillActionExecutor<CTX> {

    private static final Logger LOGGER = Logger.getLogger(SkillActionExecutor.class.getName());

    private final ActionHandlerRegistry<CTX> actionHandlers;
    private final ActionConditionEvaluator<CTX> conditionEvaluator;

    public SkillActionExecutor(ActionHandlerRegistry<CTX> actionHandlers,
                               ActionConditionEvaluator<CTX> conditionEvaluator) {
        this.actionHandlers = actionHandlers;
        this.conditionEvaluator = conditionEvaluator;
    }

    public boolean executeAll(List<Map<String, Object>> actions, CTX context) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        boolean executedAny = false;
        for (Map<String, Object> action : actions) {
            executedAny |= execute(action, context);
        }
        return executedAny;
    }

    public boolean execute(Map<String, Object> action, CTX context) {
        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveAction(action);
        Object type = resolved.get("type");
        if (!(type instanceof String typeId) || typeId.isBlank()) {
            LOGGER.warning("[SkillActionExecutor] Action without type: " + resolved);
            return false;
        }
        if (!conditionEvaluator.evaluateAll(extractConditions(resolved), context)) {
            return false;
        }

        SkillActionHandler<CTX> handler = actionHandlers.find(typeId);
        if (handler == null) {
            LOGGER.warning("[SkillActionExecutor] Unsupported action type: " + typeId);
            return false;
        }
        return handler.execute(resolved, context);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractConditions(Map<String, Object> action) {
        Object conditionsObj = action.get("conditions");
        if (!(conditionsObj instanceof List<?> rawConditions) || rawConditions.isEmpty()) {
            return List.of();
        }
        try {
            return (List<Map<String, Object>>) rawConditions;
        } catch (ClassCastException e) {
            LOGGER.warning("[SkillActionExecutor] Malformed action conditions: " + action);
            return List.of();
        }
    }
}
