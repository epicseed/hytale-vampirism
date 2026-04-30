package com.epicseed.epiccore.skill.ui;

public record SkillTreeNodeStateView(boolean wip,
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
