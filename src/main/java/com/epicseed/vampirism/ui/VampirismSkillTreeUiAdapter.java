package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.progression.SkillNodeState;
import com.epicseed.epiccore.skill.progression.SkillUnlockResult;
import com.epicseed.epiccore.skill.ui.SkillTreeLayoutBounds;
import com.epicseed.epiccore.skill.ui.SkillTreeNodeStateView;
import com.epicseed.epiccore.skill.ui.SkillTreeUiAdapter;
import com.epicseed.epiccore.skill.ui.SkillTreeUnlockResultView;
import com.epicseed.vampirism.domain.skill.SkillTreePresenter;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.hypixel.hytale.math.vector.Vector2d;

public final class VampirismSkillTreeUiAdapter implements SkillTreeUiAdapter {

    private final ProgressionDefinitionProvider definitionProvider;
    private final SkillProgressionAccess progressionAccess;
    private final Supplier<Vector2d> highestPositionSupplier;
    private final SkillTreePresenter presenter;
    private final SkillTreeManager skillTreeManager;

    public VampirismSkillTreeUiAdapter(@Nonnull ProgressionDefinitionProvider definitionProvider,
                                       @Nonnull SkillProgressionAccess progressionAccess,
                                       @Nonnull Supplier<Vector2d> highestPositionSupplier,
                                       @Nonnull SkillTreePresenter presenter,
                                       @Nonnull SkillTreeManager skillTreeManager) {
        this.definitionProvider = definitionProvider;
        this.progressionAccess = progressionAccess;
        this.highestPositionSupplier = highestPositionSupplier;
        this.presenter = presenter;
        this.skillTreeManager = skillTreeManager;
    }

    @Override
    @Nonnull
    public List<Skill> allSkills() {
        return new ArrayList<>(definitionProvider.getAllSkills());
    }

    @Override
    @Nullable
    public Skill skill(@Nonnull String skillId) {
        return definitionProvider.getSkill(skillId);
    }

    @Override
    @Nonnull
    public SkillTreeLayoutBounds layoutBounds() {
        Vector2d highestPosition = highestPositionSupplier.get();
        return new SkillTreeLayoutBounds((int) highestPosition.getX(), (int) highestPosition.getY());
    }

    @Override
    public int availablePoints(@Nonnull UUID uuid) {
        return progressionAccess.getSkillPoints(uuid);
    }

    @Override
    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return progressionAccess.hasSkill(uuid, skillId);
    }

    @Override
    @Nonnull
    public SkillTreeNodeStateView stateFor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        SkillNodeState state = presenter.stateFor(skill, uuid);
        return new SkillTreeNodeStateView(
                state.wip(),
                state.unlocked(),
                state.canUnlock(),
                state.depsMet(),
                state.availablePoints(),
                state.costText(),
                state.unlockStatus(),
                state.indicatorColor());
    }

    @Override
    @Nonnull
    public String buildDescription(@Nonnull Skill skill) {
        return presenter.buildDescription(skill);
    }

    @Override
    @Nonnull
    public SkillTreeUnlockResultView unlock(@Nonnull UUID uuid, @Nonnull Skill skill) {
        SkillUnlockResult result = skillTreeManager.unlockDetailed(uuid, skill);
        return new SkillTreeUnlockResultView(result.unlocked(), result.message());
    }

    @Override
    public void resetPlayer(@Nonnull UUID uuid) {
        skillTreeManager.resetPlayer(uuid);
    }
}
