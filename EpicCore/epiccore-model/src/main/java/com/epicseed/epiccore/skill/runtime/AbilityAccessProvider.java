package com.epicseed.epiccore.skill.runtime;

import java.util.Set;
import java.util.UUID;

public interface AbilityAccessProvider {

    boolean allowsTemporaryAbility(UUID uuid, String abilityId);

    Set<String> getUnlockedSkillIds(UUID uuid);
}
