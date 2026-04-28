package com.epicseed.vampirism.domain.skill;

public record SkillNodeState(boolean wip,
                             boolean unlocked,
                             boolean canUnlock,
                             boolean depsMet,
                             int availablePoints,
                             String costText,
                             String unlockStatus,
                             String indicatorColor) {

    public boolean unlockButtonDisabled() {
        return wip || unlocked || !canUnlock;
    }
}
