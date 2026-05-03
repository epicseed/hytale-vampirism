package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.runtime.actions.ActionConditionEvaluator;
import com.epicseed.epiccore.skill.runtime.requirements.ConfigurableRequirementEvaluator;

public final class SkillRequirementEvaluator {

    private static final SkillProgressionAccess UNINITIALIZED_ACCESS_PROVIDER =
            new SkillProgressionAccess() {
                @Override
                public int getSkillPoints(@Nonnull UUID uuid) {
                    return 0;
                }

                @Override
                public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
                    return false;
                }

                @Override
                public boolean canUnlock(@Nonnull UUID uuid,
                                         @Nonnull String skillId,
                                         int cost,
                                         @Nonnull Iterable<String> requirementIds) {
                    return false;
                }

                @Override
                public boolean tryUnlock(@Nonnull UUID uuid,
                                         @Nonnull String skillId,
                                         int cost,
                                         @Nonnull Iterable<String> requirementIds) {
                    return false;
                }

                @Override
                public boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
                    return false;
                }

                @Override
                public @Nonnull Set<String> getUnlockedSkillIds(@Nonnull UUID uuid) {
                    return Set.of();
                }

                @Override
                public void resetSkills(@Nonnull UUID uuid) {
                }
            };
    private final ConfigurableRequirementEvaluator<SkillRuntimeContext> evaluator;

    public SkillRequirementEvaluator(@Nonnull ProgressionDefinitionProvider definitions,
                                     @Nonnull SkillConditionEvaluator conditionEvaluator) {
        this(definitions, conditionEvaluator::evaluateAll, () -> UNINITIALIZED_ACCESS_PROVIDER);
    }

    public SkillRequirementEvaluator(@Nonnull ProgressionDefinitionProvider definitions,
                                     @Nonnull SkillConditionEvaluator conditionEvaluator,
                                     @Nonnull Supplier<? extends SkillProgressionAccess> fallbackAccessSupplier) {
        this(definitions, conditionEvaluator::evaluateAll, fallbackAccessSupplier);
    }

    public SkillRequirementEvaluator(@Nonnull ProgressionDefinitionProvider definitions,
                                     @Nonnull ActionConditionEvaluator<SkillRuntimeContext> conditionEvaluator) {
        this(definitions, conditionEvaluator, () -> UNINITIALIZED_ACCESS_PROVIDER);
    }

    public SkillRequirementEvaluator(@Nonnull ProgressionDefinitionProvider definitions,
                                     @Nonnull ActionConditionEvaluator<SkillRuntimeContext> conditionEvaluator,
                                     @Nonnull Supplier<? extends SkillProgressionAccess> fallbackAccessSupplier) {
        this.evaluator = new ConfigurableRequirementEvaluator<>(
                conditionEvaluator,
                fallbackAccessSupplier,
                definitions,
                SkillRuntimeContext::uuid);
    }

    public void init(@Nonnull SkillProgressionAccess accessProvider) {
        evaluator.init(accessProvider);
    }

    void resetForTests() {
        evaluator.reset();
    }

    public boolean evaluateAll(List<Map<String, Object>> requirements, SkillRuntimeContext ctx) {
        return evaluator.evaluateAll(requirements, ctx);
    }

    public boolean evaluate(Map<String, Object> requirement, SkillRuntimeContext ctx) {
        return evaluator.evaluate(requirement, ctx);
    }
}
