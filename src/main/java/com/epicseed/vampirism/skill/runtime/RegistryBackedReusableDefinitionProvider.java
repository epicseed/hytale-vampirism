package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.runtime.ReusableDefinitionProvider;
import com.epicseed.vampirism.skill.registry.ReusableDefRegistry;

import javax.annotation.Nullable;

public final class RegistryBackedReusableDefinitionProvider implements ReusableDefinitionProvider {

    private final ReusableDefRegistry conditionRegistry;
    private final ReusableDefRegistry requirementRegistry;
    private final ReusableDefRegistry triggerRegistry;
    private final ReusableDefRegistry actionRegistry;
    private final ReusableDefRegistry targetingRegistry;

    public RegistryBackedReusableDefinitionProvider(ReusableDefRegistry conditionRegistry,
                                                    ReusableDefRegistry requirementRegistry,
                                                    ReusableDefRegistry triggerRegistry,
                                                    ReusableDefRegistry actionRegistry,
                                                    ReusableDefRegistry targetingRegistry) {
        this.conditionRegistry = conditionRegistry;
        this.requirementRegistry = requirementRegistry;
        this.triggerRegistry = triggerRegistry;
        this.actionRegistry = actionRegistry;
        this.targetingRegistry = targetingRegistry;
    }

    @Override
    @Nullable
    public ReusableDef get(String kind, String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        return switch (kind) {
            case "condition" -> conditionRegistry.Get(id);
            case "requirement" -> requirementRegistry.Get(id);
            case "trigger" -> triggerRegistry.Get(id);
            case "action" -> actionRegistry.Get(id);
            case "targeting" -> targetingRegistry.Get(id);
            default -> null;
        };
    }
}
