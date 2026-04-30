package com.epicseed.epiccore.skill.progression;

import java.util.Set;
import java.util.UUID;

public interface SkillProgressionAccess {

    int getSkillPoints(UUID uuid);

    boolean hasSkill(UUID uuid, String skillId);

    boolean canUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds);

    boolean tryUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds);

    boolean grantSkill(UUID uuid, String skillId);

    Set<String> getUnlockedSkillIds(UUID uuid);

    void resetSkills(UUID uuid);
}
