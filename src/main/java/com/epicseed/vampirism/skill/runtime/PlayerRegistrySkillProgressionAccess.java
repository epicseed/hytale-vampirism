package com.epicseed.vampirism.skill.runtime;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;

public final class PlayerRegistrySkillProgressionAccess implements SkillProgressionAccess {

    private static PlayerRegistrySkillProgressionAccess instance;

    interface Backend {
        int getSkillPoints(@Nonnull UUID uuid);
        int getAcquiredSkillPoints(@Nonnull UUID uuid);
        void addSkillPoints(@Nonnull UUID uuid, int amount);
        void setSkillPoints(@Nonnull UUID uuid, int amount);
        boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId);
        boolean canUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost, @Nonnull Iterable<String> requirementIds);
        boolean tryUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost, @Nonnull Iterable<String> requirementIds);
        boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId);
        @Nonnull Set<String> getUnlockedSkillIds(@Nonnull UUID uuid);
        void resetSkills(@Nonnull UUID uuid);
    }

    private final Backend backend;

    private PlayerRegistrySkillProgressionAccess(@Nonnull Backend backend) {
        this.backend = backend;
    }

    @Nonnull
    public static PlayerRegistrySkillProgressionAccess init(@Nonnull PlayerSkillRegistry playerSkillRegistry) {
        return init(new Backend() {
            @Override
            public int getSkillPoints(@Nonnull UUID uuid) {
                return playerSkillRegistry.getSkillPoints(uuid);
            }

            @Override
            public int getAcquiredSkillPoints(@Nonnull UUID uuid) {
                return playerSkillRegistry.getAcquiredSkillPoints(uuid);
            }

            @Override
            public void addSkillPoints(@Nonnull UUID uuid, int amount) {
                playerSkillRegistry.addSkillPoints(uuid, amount);
            }

            @Override
            public void setSkillPoints(@Nonnull UUID uuid, int amount) {
                playerSkillRegistry.setSkillPoints(uuid, amount);
            }

            @Override
            public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
                return playerSkillRegistry.hasSkill(uuid, skillId);
            }

            @Override
            public boolean canUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost, @Nonnull Iterable<String> requirementIds) {
                return playerSkillRegistry.canUnlock(uuid, skillId, cost, requirementIds);
            }

            @Override
            public boolean tryUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost, @Nonnull Iterable<String> requirementIds) {
                return playerSkillRegistry.tryUnlock(uuid, skillId, cost, requirementIds);
            }

            @Override
            public boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
                return playerSkillRegistry.grantSkill(uuid, skillId);
            }

            @Override
            @Nonnull
            public Set<String> getUnlockedSkillIds(@Nonnull UUID uuid) {
                return playerSkillRegistry.getUnlockedSkills(uuid);
            }

            @Override
            public void resetSkills(@Nonnull UUID uuid) {
                playerSkillRegistry.resetSkills(uuid);
            }
        });
    }

    @Nonnull
    static PlayerRegistrySkillProgressionAccess init(@Nonnull Backend backend) {
        instance = new PlayerRegistrySkillProgressionAccess(backend);
        return instance;
    }

    @Nonnull
    public static PlayerRegistrySkillProgressionAccess instance() {
        if (instance == null) throw new IllegalStateException("PlayerRegistrySkillProgressionAccess not initialized!");
        return instance;
    }

    @Nonnull
    static SkillProgressionAccess instanceOr(@Nonnull SkillProgressionAccess fallback) {
        return instance != null ? instance : fallback;
    }

    static void resetForTests() {
        instance = null;
    }

    public int getAcquiredSkillPoints(@Nonnull UUID uuid) {
        return backend.getAcquiredSkillPoints(uuid);
    }

    public void addSkillPoints(@Nonnull UUID uuid, int amount) {
        backend.addSkillPoints(uuid, amount);
    }

    public void setSkillPoints(@Nonnull UUID uuid, int amount) {
        backend.setSkillPoints(uuid, amount);
    }

    @Override
    public int getSkillPoints(@Nonnull UUID uuid) {
        return backend.getSkillPoints(uuid);
    }

    @Override
    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return backend.hasSkill(uuid, skillId);
    }

    @Override
    public boolean canUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost, @Nonnull Iterable<String> requirementIds) {
        return backend.canUnlock(uuid, skillId, cost, requirementIds);
    }

    @Override
    public boolean tryUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost, @Nonnull Iterable<String> requirementIds) {
        return backend.tryUnlock(uuid, skillId, cost, requirementIds);
    }

    @Override
    public boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return backend.grantSkill(uuid, skillId);
    }

    @Override
    @Nonnull
    public Set<String> getUnlockedSkillIds(@Nonnull UUID uuid) {
        return backend.getUnlockedSkillIds(uuid);
    }

    @Override
    public void resetSkills(@Nonnull UUID uuid) {
        backend.resetSkills(uuid);
    }
}
