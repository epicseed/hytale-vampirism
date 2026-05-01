package com.epicseed.vampirism.skill.data;

public final class SkillDataPaths {

    private SkillDataPaths() {
    }

    public static com.epicseed.epiccore.skill.data.SkillDataPaths vampirismDefaults() {
        return new com.epicseed.epiccore.skill.data.SkillDataPaths(
                "data/vampirism/skills",
                "Common/UI/Custom/Vampirism/Data/SkillsData",
                "Common/UI/Custom/Vampirism/Data/SkillsData.json");
    }
}
