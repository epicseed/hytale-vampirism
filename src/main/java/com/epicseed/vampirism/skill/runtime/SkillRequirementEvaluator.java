package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.runtime.CatalogBackedProgressionDefinitionProvider;
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
    private static final ConfigurableRequirementEvaluator<SkillRuntimeContext> EVALUATOR =
            new ConfigurableRequirementEvaluator<>(
                    (condition, ctx) -> SkillConditionEvaluator.evaluateAll(condition, ctx),
                    () -> PlayerRegistrySkillProgressionAccess.instanceOr(UNINITIALIZED_ACCESS_PROVIDER),
                    CatalogBackedProgressionDefinitionProvider.instance(),
                    SkillRuntimeContext::uuid);

    private SkillRequirementEvaluator() {}

    public static void init(SkillProgressionAccess accessProvider) {
        EVALUATOR.init(accessProvider);
    }

    static void resetForTests() {
        EVALUATOR.reset();
    }

    public static boolean evaluateAll(List<Map<String, Object>> requirements, SkillRuntimeContext ctx) {
        return EVALUATOR.evaluateAll(requirements, ctx);
    }

    public static boolean evaluate(Map<String, Object> requirement, SkillRuntimeContext ctx) {
        return EVALUATOR.evaluate(requirement, ctx);
    }
}
