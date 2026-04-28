package com.epicseed.vampirism.domain.skill;

import javax.annotation.Nonnull;

public record SkillUnlockResult(@Nonnull SkillUnlockStatus status,
                                @Nonnull String skillId,
                                @Nonnull String message) {

    public boolean unlocked() {
        return status == SkillUnlockStatus.UNLOCKED;
    }

    public boolean canUnlock() {
        return status == SkillUnlockStatus.CAN_UNLOCK;
    }
}
