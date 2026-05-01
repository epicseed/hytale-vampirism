package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.model.Skill;
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
    private static final RegistryBackedRequirementEvaluator<SkillRuntimeContext> EVALUATOR =
            new RegistryBackedRequirementEvaluator<>(createHandlers());
    private static volatile SkillProgressionAccess accessProvider =
            UNINITIALIZED_ACCESS_PROVIDER;

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
                .register("abilityUnlocked", (requirement, ctx) -> hasUnlockedAbility(ctx, stringValue(requirement, "abilityId")))
                .register("passiveUnlocked", (requirement, ctx) -> hasUnlockedPassive(ctx, stringValue(requirement, "passiveId")));
    }

    private static boolean hasUnlockedAbility(SkillRuntimeContext ctx, String abilityId) {
        if (ctx.uuid() == null || abilityId == null || abilityId.isBlank()) return false;
        for (String unlockedSkillId : accessProvider.getUnlockedSkillIds(ctx.uuid())) {
            Skill skill = VampirismProgressionDefinitionProvider.instance().getSkill(unlockedSkillId);
            if (skill != null && abilityId.equals(skill.abilityId)) return true;
        }
        return false;
    }

    private static boolean hasUnlockedPassive(SkillRuntimeContext ctx, String passiveId) {
        if (ctx.uuid() == null || passiveId == null || passiveId.isBlank()) return false;
        for (String unlockedSkillId : accessProvider.getUnlockedSkillIds(ctx.uuid())) {
            Skill skill = VampirismProgressionDefinitionProvider.instance().getSkill(unlockedSkillId);
            if (skill != null && passiveId.equals(skill.passiveId)) return true;
        }
        return false;
    }

    private static String stringValue(Map<String, Object> value, String key) {
        Object raw = value.get(key);
        return raw instanceof String string ? string : null;
    }
}
