package com.epicseed.vampirism.domain.skill;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillNodeState;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.progression.SkillTreeOperations;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.runtime.PlayerRegistrySkillProgressionAccess;
import com.epicseed.vampirism.skill.runtime.VampirismProgressionDefinitionProvider;

public final class SkillTreePresenter {

    private static final SkillProgressionAccess PROGRESSION = PlayerRegistrySkillProgressionAccess.instance();
    private static final ProgressionDefinitionProvider DEFINITIONS = VampirismProgressionDefinitionProvider.instance();

    private SkillTreePresenter() {
    }

    @Nonnull
    public static SkillNodeState stateFor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        return SkillTreeOperations.stateFor(skill, uuid, PROGRESSION);
    }

    @Nonnull
    public static String buildDescription(@Nonnull Skill skill) {
        return SkillTreeOperations.buildDescription(skill, DEFINITIONS);
    }

    @Nonnull
    public static String unlockFailureReason(@Nonnull Skill skill, @Nonnull UUID uuid) {
        return SkillTreeOperations.evaluateUnlock(uuid, skill, PROGRESSION).message();
    }

    @Nonnull
    public static String indicatorColor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        return SkillTreeOperations.indicatorColor(skill, uuid, PROGRESSION);
    }
}
