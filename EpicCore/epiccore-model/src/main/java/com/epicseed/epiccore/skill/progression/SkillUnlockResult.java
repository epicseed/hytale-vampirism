package com.epicseed.epiccore.skill.progression;

public record SkillUnlockResult(SkillUnlockStatus status,
                                String skillId,
                                String message) {

    public boolean unlocked() {
        return status == SkillUnlockStatus.UNLOCKED;
    }

    public boolean canUnlock() {
        return status == SkillUnlockStatus.CAN_UNLOCK;
    }
}
