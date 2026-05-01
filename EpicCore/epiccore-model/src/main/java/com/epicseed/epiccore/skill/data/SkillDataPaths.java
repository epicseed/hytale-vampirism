package com.epicseed.epiccore.skill.data;

public record SkillDataPaths(String primaryDataDir,
                             String fallbackDataDir,
                             String legacyJsonPath) {
}
