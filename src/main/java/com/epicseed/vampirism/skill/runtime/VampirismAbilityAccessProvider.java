package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.skill.runtime.AbilityAccessProvider;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.systems.VampireInfectionSystem;

import java.util.Set;
import java.util.UUID;

public final class VampirismAbilityAccessProvider implements AbilityAccessProvider {

    private final PlayerSkillRegistry playerSkillRegistry;

    public VampirismAbilityAccessProvider(PlayerSkillRegistry playerSkillRegistry) {
        this.playerSkillRegistry = playerSkillRegistry;
    }

    @Override
    public boolean allowsTemporaryAbility(UUID uuid, String abilityId) {
        return VampireInfectionSystem.allowsTemporaryAbility(uuid, abilityId);
    }

    @Override
    public Set<String> getUnlockedSkillIds(UUID uuid) {
        return playerSkillRegistry.getUnlockedSkills(uuid);
    }
}
