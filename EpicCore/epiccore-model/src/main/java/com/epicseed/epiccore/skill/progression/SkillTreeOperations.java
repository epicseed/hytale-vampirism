package com.epicseed.epiccore.skill.progression;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;

public final class SkillTreeOperations {

    private SkillTreeOperations() {
    }

    public static SkillUnlockResult unlock(UUID uuid,
                                           Skill skill,
                                           SkillProgressionAccess access) {
        SkillUnlockResult precheck = evaluateUnlock(uuid, skill, access);
        if (!precheck.canUnlock()) {
            return precheck;
        }

        boolean success = access.tryUnlock(uuid, skill.id, skill.cost, requirementIds(skill));
        if (success) {
            return result(SkillUnlockStatus.UNLOCKED, skill, "Skill unlocked: " + skill.displayName + "!");
        }
        return result(SkillUnlockStatus.FAILED, skill, "Could not unlock: " + skill.displayName + ".");
    }

    public static SkillUnlockResult evaluateUnlock(UUID uuid,
                                                   Skill skill,
                                                   SkillProgressionAccess access) {
        if (!skill.enabled) {
            return result(SkillUnlockStatus.DISABLED, skill, "Skill is WIP: " + skill.displayName + ".");
        }
        if (access.hasSkill(uuid, skill.id)) {
            return result(SkillUnlockStatus.ALREADY_UNLOCKED, skill, "Skill already unlocked: " + skill.displayName + ".");
        }

        List<String> requirementIds = requirementIds(skill);
        for (String requirementId : requirementIds) {
            if (!access.hasSkill(uuid, requirementId)) {
                return result(SkillUnlockStatus.MISSING_REQUIREMENTS, skill,
                        "Requirements not met for: " + skill.displayName + ".");
            }
        }

        int points = access.getSkillPoints(uuid);
        if (points < skill.cost) {
            return result(SkillUnlockStatus.INSUFFICIENT_POINTS, skill,
                    "Insufficient points for: " + skill.displayName
                            + " (required: " + skill.cost + ", available: " + points + ").");
        }
        if (!access.canUnlock(uuid, skill.id, skill.cost, requirementIds)) {
            return result(SkillUnlockStatus.FAILED, skill, "Could not unlock: " + skill.displayName + ".");
        }
        return result(SkillUnlockStatus.CAN_UNLOCK, skill, "Skill can be unlocked: " + skill.displayName + ".");
    }

    public static boolean grant(UUID uuid,
                                Skill skill,
                                SkillProgressionAccess access) {
        return skill.enabled && access.grantSkill(uuid, skill.id);
    }

    public static SkillNodeState stateFor(Skill skill,
                                          UUID uuid,
                                          SkillProgressionAccess access) {
        boolean wip = !skill.enabled;
        boolean unlocked = access.hasSkill(uuid, skill.id);
        boolean depsMet = dependenciesMet(skill, uuid, access);
        int availablePoints = access.getSkillPoints(uuid);
        boolean canUnlock = !wip && !unlocked && depsMet && availablePoints >= skill.cost;
        String costText = skill.cost == 1 ? "1 point" : skill.cost + " points";
        String unlockStatus = wip ? "WIP"
                : unlocked ? "Unlocked"
                : depsMet ? "Unlock (" + costText + ")"
                : "Locked";
        return new SkillNodeState(wip, unlocked, canUnlock, depsMet, availablePoints, costText,
                unlockStatus, indicatorColor(skill, uuid, access));
    }

    public static String buildDescription(Skill skill,
                                          ProgressionDefinitionProvider definitions) {
        StringBuilder sb = new StringBuilder(skill.description != null ? skill.description : "");

        if (skill.abilityId != null && !skill.abilityId.isBlank()) {
            Ability ability = definitions.getAbility(skill.abilityId);
            if (ability != null) {
                sb.append("\n\n[Active]");
                if (ability.bloodCost > 0) {
                    sb.append("  Blood: ").append(ability.bloodCost);
                }
                if (ability.cooldown > 0) {
                    sb.append("  Cooldown: ")
                            .append(String.format(Locale.ROOT, "%.1f", ability.cooldown))
                            .append("s");
                }
            }
        } else if (skill.passiveId != null && !skill.passiveId.isBlank()) {
            Passive passive = definitions.getPassive(skill.passiveId);
            sb.append("\n\n[Passive]");
            if (passive != null && !passive.modifiers.isEmpty()) {
                sb.append("  Modifiers: ").append(passive.modifiers.size());
            }
        }

        return sb.toString();
    }

    public static String indicatorColor(Skill skill,
                                        UUID uuid,
                                        SkillProgressionAccess access) {
        if (!skill.enabled) {
            return "#000000";
        }
        if (access.hasSkill(uuid, skill.id)) {
            return "#00cc44";
        }
        if (!dependenciesMet(skill, uuid, access)) {
            return "#cc3333";
        }
        return access.getSkillPoints(uuid) < skill.cost ? "#888888" : "#ffffff";
    }

    public static boolean dependenciesMet(Skill skill,
                                          UUID uuid,
                                          SkillProgressionAccess access) {
        List<Skill> requirements = skill.requires != null ? skill.requires : Collections.emptyList();
        return requirements.isEmpty() || requirements.stream().allMatch(req -> access.hasSkill(uuid, req.id));
    }

    public static List<String> requirementIds(Skill skill) {
        List<Skill> requirements = skill.requires != null ? skill.requires : Collections.emptyList();
        return requirements.stream().map(req -> req.id).collect(Collectors.toList());
    }

    private static SkillUnlockResult result(SkillUnlockStatus status,
                                            Skill skill,
                                            String message) {
        return new SkillUnlockResult(status, skill.id, message);
    }
}
