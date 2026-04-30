package com.epicseed.vampirism.skill.data;

public record SkillDataPaths(String primaryDataDir,
                             String fallbackDataDir,
                             String legacyJsonPath) {

    public static SkillDataPaths vampirismDefaults() {
        return new SkillDataPaths(
                "data/vampirism/skills",
                "Common/UI/Custom/Vampirism/Data/SkillsData",
                "Common/UI/Custom/Vampirism/Data/SkillsData.json");
    }
}
