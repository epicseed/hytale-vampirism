package com.epicseed.vampirism.skill.runtime;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;

public final class PlayerRegistrySkillProgressionAccess implements SkillProgressionAccess {

    private static final PlayerRegistrySkillProgressionAccess INSTANCE = new PlayerRegistrySkillProgressionAccess();

    private PlayerRegistrySkillProgressionAccess() {
    }

    public static PlayerRegistrySkillProgressionAccess instance() {
        return INSTANCE;
    }

    @Override
    public int getSkillPoints(@Nonnull UUID uuid) {
        return PlayerSkillRegistry.get().getSkillPoints(uuid);
    }

    @Override
    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return PlayerSkillRegistry.get().hasSkill(uuid, skillId);
    }

    @Override
    public boolean canUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost, @Nonnull Iterable<String> requirementIds) {
        return PlayerSkillRegistry.get().canUnlock(uuid, skillId, cost, requirementIds);
    }

    @Override
    public boolean tryUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost, @Nonnull Iterable<String> requirementIds) {
        return PlayerSkillRegistry.get().tryUnlock(uuid, skillId, cost, requirementIds);
    }

    @Override
    public boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return PlayerSkillRegistry.get().grantSkill(uuid, skillId);
    }

    @Override
    @Nonnull
    public Set<String> getUnlockedSkillIds(@Nonnull UUID uuid) {
        return PlayerSkillRegistry.get().getUnlockedSkills(uuid);
    }

    @Override
    public void resetSkills(@Nonnull UUID uuid) {
        PlayerSkillRegistry.get().resetSkills(uuid);
    }
}
