package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.ui.SkillTreeLayoutBounds;
import com.epicseed.epiccore.skill.ui.SkillTreeNodeStateView;
import com.epicseed.epiccore.skill.ui.SkillTreeUiAdapter;
import com.epicseed.epiccore.skill.ui.SkillTreeUnlockResultView;
import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.domain.skill.SkillNodeState;
import com.epicseed.vampirism.domain.skill.SkillTreePresenter;
import com.epicseed.vampirism.domain.skill.SkillUnlockResult;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.math.vector.Vector2d;

public final class VampirismSkillTreeUiAdapter implements SkillTreeUiAdapter {

    private static final VampirismSkillTreeUiAdapter INSTANCE = new VampirismSkillTreeUiAdapter();

    private VampirismSkillTreeUiAdapter() {
    }

    public static VampirismSkillTreeUiAdapter instance() {
        return INSTANCE;
    }

    @Override
    @Nonnull
    public List<Skill> allSkills() {
        return new ArrayList<>(Vampirism.getInstance().GetSkillRegistry().GetAll());
    }

    @Override
    @Nullable
    public Skill skill(@Nonnull String skillId) {
        return Vampirism.getInstance().GetSkillRegistry().GetSkill(skillId);
    }

    @Override
    @Nonnull
    public SkillTreeLayoutBounds layoutBounds() {
        Vector2d highestPosition = Vampirism.getInstance().GetHighestPosition();
        return new SkillTreeLayoutBounds((int) highestPosition.getX(), (int) highestPosition.getY());
    }

    @Override
    public int availablePoints(@Nonnull UUID uuid) {
        return PlayerSkillRegistry.get().getSkillPoints(uuid);
    }

    @Override
    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return PlayerSkillRegistry.get().hasSkill(uuid, skillId);
    }

    @Override
    @Nonnull
    public SkillTreeNodeStateView stateFor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        SkillNodeState state = SkillTreePresenter.stateFor(skill, uuid);
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
        return SkillTreePresenter.buildDescription(skill);
    }

    @Override
    @Nonnull
    public SkillTreeUnlockResultView unlock(@Nonnull UUID uuid, @Nonnull Skill skill) {
        SkillUnlockResult result = SkillTreeManager.get().unlockDetailed(uuid, skill);
        return new SkillTreeUnlockResultView(result.unlocked(), result.message());
    }

    @Override
    public void resetPlayer(@Nonnull UUID uuid) {
        SkillTreeManager.get().resetPlayer(uuid);
    }
}
