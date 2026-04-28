package com.epicseed.vampirism.skill.manager;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.domain.skill.SkillUnlockResult;
import com.epicseed.vampirism.domain.skill.SkillUnlockStatus;
import com.epicseed.vampirism.modifier.ModifierTag;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.StatModifier;
import com.epicseed.vampirism.skill.model.InlineModifier;
import com.epicseed.vampirism.skill.model.Passive;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.runtime.ModifierScopeMatcher;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class SkillTreeManager {

    private static SkillTreeManager instance;

    private final SkillRegistry registry;

    public SkillTreeManager(SkillRegistry registry) {
        this.registry = registry;
        instance = this;
    }

    public static SkillTreeManager get() {
        if (instance == null) throw new IllegalStateException("SkillTreeManager not initialized!");
        return instance;
    }

    public boolean canUnlock(@Nonnull UUID uuid, Skill skill) {
        return evaluateUnlock(uuid, skill).canUnlock();
    }

    /**
     * Atomically checks requirements, deducts the cost, and unlocks the skill.
     * On success, registers per-player modifiers for each of the skill's effects
     * and for any corresponding passive definition.
     *
     * @return true if the skill was unlocked, false if preconditions were not met.
     */
    public boolean unlock(@Nonnull UUID uuid, Skill skill) {
        return unlockDetailed(uuid, skill).unlocked();
    }

    @Nonnull
    public SkillUnlockResult unlockDetailed(@Nonnull UUID uuid, @Nonnull Skill skill) {
        SkillUnlockResult precheck = evaluateUnlock(uuid, skill);
        if (!precheck.canUnlock()) {
            return precheck;
        }
        List<Skill> reqs = skill.requires != null ? skill.requires : Collections.emptyList();
        List<String> reqIds = reqs.stream().map(s -> s.id).collect(Collectors.toList());
        boolean success = PlayerSkillRegistry.get().tryUnlock(uuid, skill.id, skill.cost, reqIds);
        if (success) {
            registerSkillModifiers(uuid, skill);
            return result(SkillUnlockStatus.UNLOCKED, skill, "Skill unlocked: " + skill.displayName + "!");
        }
        return result(SkillUnlockStatus.FAILED, skill, "Could not unlock: " + skill.displayName + ".");
    }

    @Nonnull
    public SkillUnlockResult evaluateUnlock(@Nonnull UUID uuid, @Nonnull Skill skill) {
        if (!skill.enabled) {
            return result(SkillUnlockStatus.DISABLED, skill, "Skill is WIP: " + skill.displayName + ".");
        }
        if (PlayerSkillRegistry.get().hasSkill(uuid, skill.id)) {
            return result(SkillUnlockStatus.ALREADY_UNLOCKED, skill, "Skill already unlocked: " + skill.displayName + ".");
        }
        List<Skill> reqs = skill.requires != null ? skill.requires : Collections.emptyList();
        List<String> reqIds = reqs.stream().map(s -> s.id).collect(Collectors.toList());
        for (String reqId : reqIds) {
            if (!PlayerSkillRegistry.get().hasSkill(uuid, reqId)) {
                return result(SkillUnlockStatus.MISSING_REQUIREMENTS, skill, "Requirements not met for: " + skill.displayName + ".");
            }
        }
        int points = PlayerSkillRegistry.get().getSkillPoints(uuid);
        if (points < skill.cost) {
            return result(SkillUnlockStatus.INSUFFICIENT_POINTS, skill,
                    "Insufficient points for: " + skill.displayName
                            + " (required: " + skill.cost + ", available: " + points + ").");
        }
        if (!PlayerSkillRegistry.get().canUnlock(uuid, skill.id, skill.cost, reqIds)) {
            return result(SkillUnlockStatus.FAILED, skill, "Could not unlock: " + skill.displayName + ".");
        }
        return result(SkillUnlockStatus.CAN_UNLOCK, skill, "Skill can be unlocked: " + skill.displayName + ".");
    }

    public boolean grant(@Nonnull UUID uuid, @Nonnull Skill skill) {
        if (!skill.enabled) {
            return false;
        }
        boolean success = PlayerSkillRegistry.get().grantSkill(uuid, skill.id);
        if (success) {
            registerSkillModifiers(uuid, skill);
        }
        return success;
    }

    /** Refunds all spent points, clears all unlocked skills, and removes their modifiers. */
    public void resetPlayer(@Nonnull UUID uuid) {
        PlayerSkillRegistry.get().resetSkills(uuid);
        ModifierRegistry.get().unregisterByTagPrefix(uuid, "skill:");
    }

    /** Remove all modifier registrations for a player (e.g. on disconnect). */
    public void evictPlayer(@Nonnull UUID uuid) {
        ModifierRegistry.get().unregisterByTagPrefix(uuid, "skill:");
    }

    /**
     * Re-registers all skill modifiers for a player who reconnects.
     * Call this on join once the player's skill data has been loaded.
     */
    public void reloadModifiers(@Nonnull UUID uuid) {
        ModifierRegistry.get().unregisterByTagPrefix(uuid, "skill:");
        for (String skillId : PlayerSkillRegistry.get().getUnlockedSkills(uuid)) {
            Skill skill = registry.GetSkill(skillId);
            if (skill != null) registerSkillModifiers(uuid, skill);
        }
    }

    // -------------------------------------------------------------------------

    private void registerSkillModifiers(@Nonnull UUID uuid, @Nonnull Skill skill) {
        registerInlineModifiers(uuid, skill.id, skill.modifiers);
        if (skill.passiveId != null && !skill.passiveId.isBlank()) {
            Passive passive = Vampirism.getInstance().GetPassiveRegistry().Get(skill.passiveId);
            if (passive != null) {
                registerInlineModifiers(uuid, skill.id, passive.modifiers);
            }
        }
    }

    private void registerInlineModifiers(@Nonnull UUID uuid, @Nonnull String sourceId,
                                         @Nonnull List<InlineModifier> modifiers) {
        ModifierRegistry reg = ModifierRegistry.get();
        for (InlineModifier mod : modifiers) {
            if (mod.stat == null) continue;
            String modifierKey = mod.modifierId != null && !mod.modifierId.isBlank() ? mod.modifierId : mod.statId;
            String tagKey = "skill:" + sourceId + ":" + modifierKey;
            StatModifier modifier = buildModifier(mod);
            reg.register(uuid, mod.stat, ModifierTag.of(tagKey), mod.priority, modifier);
        }
    }

    private StatModifier buildModifier(@Nonnull InlineModifier mod) {
        float value = mod.value;
        return switch (mod.operation) {
            case ADD      -> (current, ctx) -> ModifierScopeMatcher.applies(mod, ctx) ? current + value : current;
            case MULTIPLY -> (current, ctx) -> ModifierScopeMatcher.applies(mod, ctx) ? current * value : current;
            case OVERRIDE -> (current, ctx) -> ModifierScopeMatcher.applies(mod, ctx) ? value : current;
        };
    }

    @Nonnull
    private static SkillUnlockResult result(@Nonnull SkillUnlockStatus status, @Nonnull Skill skill, @Nonnull String message) {
        return new SkillUnlockResult(status, skill.id, message);
    }
}
