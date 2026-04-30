package com.epicseed.vampirism.domain.skill;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.skill.manager.SkillTreeManager;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;

public final class SkillTreePresenter {

    private SkillTreePresenter() {
    }

    @Nonnull
    public static SkillNodeState stateFor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        boolean wip = !skill.enabled;
        boolean unlocked = PlayerSkillRegistry.get().hasSkill(uuid, skill.id);
        boolean depsMet = dependenciesMet(skill, uuid);
        int availablePoints = PlayerSkillRegistry.get().getSkillPoints(uuid);
        boolean canUnlock = !wip && !unlocked && depsMet && availablePoints >= skill.cost;
        String costText = skill.cost == 1 ? "1 point" : skill.cost + " points";
        String unlockStatus = wip ? "WIP"
                : unlocked ? "Unlocked"
                : depsMet ? "Unlock (" + costText + ")"
                : "Locked";
        return new SkillNodeState(wip, unlocked, canUnlock, depsMet, availablePoints, costText,
                unlockStatus, indicatorColor(skill, uuid));
    }

    @Nonnull
    public static String buildDescription(@Nonnull Skill skill) {
        StringBuilder sb = new StringBuilder(skill.description != null ? skill.description : "");

        if (skill.abilityId != null && !skill.abilityId.isBlank()) {
            Ability ability = Vampirism.getInstance().GetAbilityRegistry().Get(skill.abilityId);
            if (ability != null) {
                sb.append("\n\n[Active]");
                if (ability.bloodCost > 0) {
                    sb.append("  Blood: ").append(ability.bloodCost);
                }
                if (ability.cooldown > 0) {
                    sb.append("  Cooldown: ").append(String.format("%.1f", ability.cooldown)).append("s");
                }
            }
        } else if (skill.passiveId != null && !skill.passiveId.isBlank()) {
            Passive passive = Vampirism.getInstance().GetPassiveRegistry().Get(skill.passiveId);
            sb.append("\n\n[Passive]");
            if (passive != null && !passive.modifiers.isEmpty()) {
                sb.append("  Modifiers: ").append(passive.modifiers.size());
            }
        }

        return sb.toString();
    }

    @Nonnull
    public static String unlockFailureReason(@Nonnull Skill skill, @Nonnull UUID uuid) {
        return SkillTreeManager.get().evaluateUnlock(uuid, skill).message();
    }

    @Nonnull
    public static String indicatorColor(@Nonnull Skill skill, @Nonnull UUID uuid) {
        if (!skill.enabled) {
            return "#000000";
        }
        if (PlayerSkillRegistry.get().hasSkill(uuid, skill.id)) {
            return "#00cc44";
        }
        if (!dependenciesMet(skill, uuid)) {
            return "#cc3333";
        }
        int points = PlayerSkillRegistry.get().getSkillPoints(uuid);
        if (points < skill.cost) {
            return "#888888";
        }
        return "#ffffff";
    }

    private static boolean dependenciesMet(@Nonnull Skill skill, @Nonnull UUID uuid) {
        return skill.requires.isEmpty() || skill.requires.stream()
                .allMatch(req -> PlayerSkillRegistry.get().hasSkill(uuid, req.id));
    }
}
