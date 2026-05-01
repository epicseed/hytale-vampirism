package com.epicseed.vampirism.domain.skill;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillNodeState;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.progression.SkillTreeOperations;
import com.epicseed.epiccore.skill.model.Skill;

public final class SkillTreePresenter {

    private final ProgressionDefinitionProvider definitionProvider;
    private final SkillProgressionAccess progressionAccess;

    public SkillTreePresenter(@Nonnull ProgressionDefinitionProvider definitionProvider,
                              @Nonnull SkillProgressionAccess progressionAccess) {
        this.definitionProvider = definitionProvider;
        this.progressionAccess = progressionAccess;
    }

    @Nonnull
    public SkillNodeState stateFor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        return SkillTreeOperations.stateFor(skill, uuid, progressionAccess);
    }

    @Nonnull
    public String buildDescription(@Nonnull Skill skill) {
        return SkillTreeOperations.buildDescription(skill, definitionProvider);
    }

    @Nonnull
    public String unlockFailureReason(@Nonnull Skill skill, @Nonnull UUID uuid) {
        return SkillTreeOperations.evaluateUnlock(uuid, skill, progressionAccess).message();
    }

    @Nonnull
    public String indicatorColor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        return SkillTreeOperations.indicatorColor(skill, uuid, progressionAccess);
    }
}
