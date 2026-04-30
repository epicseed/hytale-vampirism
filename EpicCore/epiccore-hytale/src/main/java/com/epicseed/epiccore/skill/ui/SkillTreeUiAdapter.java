package com.epicseed.epiccore.skill.ui;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.model.Skill;

public interface SkillTreeUiAdapter {

    @Nonnull
    List<Skill> allSkills();

    @Nullable
    Skill skill(@Nonnull String skillId);

    @Nonnull
    SkillTreeLayoutBounds layoutBounds();

    int availablePoints(@Nonnull UUID uuid);

    boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId);

    @Nonnull
    SkillTreeNodeStateView stateFor(@Nonnull Skill skill, @Nonnull UUID uuid);

    @Nonnull
    String buildDescription(@Nonnull Skill skill);

    @Nonnull
    SkillTreeUnlockResultView unlock(@Nonnull UUID uuid, @Nonnull Skill skill);

    void resetPlayer(@Nonnull UUID uuid);
}
