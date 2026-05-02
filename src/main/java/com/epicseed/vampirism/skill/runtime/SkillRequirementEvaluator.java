package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.runtime.requirements.RegistryBackedRequirementEvaluator;
import com.epicseed.epiccore.skill.runtime.requirements.RequirementHandlerRegistry;
import com.epicseed.epiccore.skill.runtime.requirements.StandardRequirementPacks;

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
    private static volatile SkillProgressionAccess accessProvider =
            UNINITIALIZED_ACCESS_PROVIDER;
    private static final SkillProgressionAccess LIVE_ACCESS_PROVIDER =
            new SkillProgressionAccess() {
                @Override
                public int getSkillPoints(@Nonnull UUID uuid) {
                    return currentAccessProvider().getSkillPoints(uuid);
                }

                @Override
                public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
                    return currentAccessProvider().hasSkill(uuid, skillId);
                }

                @Override
                public boolean canUnlock(@Nonnull UUID uuid,
                                         @Nonnull String skillId,
                                         int cost,
                                         @Nonnull Iterable<String> requirementIds) {
                    return currentAccessProvider().canUnlock(uuid, skillId, cost, requirementIds);
                }

                @Override
                public boolean tryUnlock(@Nonnull UUID uuid,
                                         @Nonnull String skillId,
                                         int cost,
                                         @Nonnull Iterable<String> requirementIds) {
                    return currentAccessProvider().tryUnlock(uuid, skillId, cost, requirementIds);
                }

                @Override
                public boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
                    return currentAccessProvider().grantSkill(uuid, skillId);
                }

                @Override
                public @Nonnull Set<String> getUnlockedSkillIds(@Nonnull UUID uuid) {
                    return currentAccessProvider().getUnlockedSkillIds(uuid);
                }

                @Override
                public void resetSkills(@Nonnull UUID uuid) {
                    currentAccessProvider().resetSkills(uuid);
                }
            };
    private static final RegistryBackedRequirementEvaluator<SkillRuntimeContext> EVALUATOR =
            new RegistryBackedRequirementEvaluator<>(createHandlers());

    private SkillRequirementEvaluator() {}

    public static void init(SkillProgressionAccess accessProvider) {
        SkillRequirementEvaluator.accessProvider = accessProvider != null
                ? accessProvider
                : UNINITIALIZED_ACCESS_PROVIDER;
    }

    static void resetForTests() {
        accessProvider = UNINITIALIZED_ACCESS_PROVIDER;
    }

    public static boolean evaluateAll(List<Map<String, Object>> requirements, SkillRuntimeContext ctx) {
        return EVALUATOR.evaluateAll(requirements, ctx);
    }

    public static boolean evaluate(Map<String, Object> requirement, SkillRuntimeContext ctx) {
        return EVALUATOR.evaluate(requirement, ctx);
    }

    private static RequirementHandlerRegistry<SkillRuntimeContext> createHandlers() {
        return new RequirementHandlerRegistry<SkillRuntimeContext>()
                .install(StandardRequirementPacks.generic((conditions, ctx) -> SkillConditionEvaluator.evaluateAll(conditions, ctx)))
                .install(StandardRequirementPacks.progression(
                        LIVE_ACCESS_PROVIDER,
                        VampirismProgressionDefinitionProvider.instance(),
                        SkillRuntimeContext::uuid));
    }

    private static SkillProgressionAccess currentAccessProvider() {
        return accessProvider != null ? accessProvider : UNINITIALIZED_ACCESS_PROVIDER;
    }
}
