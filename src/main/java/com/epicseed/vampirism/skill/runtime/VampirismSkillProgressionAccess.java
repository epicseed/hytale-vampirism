package com.epicseed.vampirism.skill.runtime;

import java.util.UUID;

import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;

public interface VampirismSkillProgressionAccess extends SkillProgressionAccess {

    int getAcquiredSkillPoints(UUID uuid);

    void addSkillPoints(UUID uuid, int amount);

    void setSkillPoints(UUID uuid, int amount);
}
