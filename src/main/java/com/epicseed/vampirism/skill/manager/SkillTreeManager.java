package com.epicseed.vampirism.skill.manager;
import com.epicseed.vampirism.modifier.ModifierContext;

import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.progression.SkillTreeOperations;
import com.epicseed.epiccore.skill.progression.SkillUnlockResult;
import com.epicseed.epiccore.modifier.ModifierTag;
import com.epicseed.epiccore.modifier.ValueModifier;
import com.epicseed.epiccore.skill.model.InlineModifier;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.runtime.PlayerRegistrySkillProgressionAccess;
import com.epicseed.vampirism.skill.runtime.VampirismProgressionDefinitionProvider;
import com.epicseed.vampirism.skill.runtime.ModifierScopeMatcher;
import com.epicseed.vampirism.skill.registry.SkillRegistry;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public class SkillTreeManager {

    private static SkillTreeManager instance;

    private final ProgressionDefinitionProvider definitionProvider;
    private final SkillProgressionAccess progressionAccess;

    public SkillTreeManager(SkillRegistry registry) {
        this(VampirismProgressionDefinitionProvider.instance(), PlayerRegistrySkillProgressionAccess.instance());
    }

    SkillTreeManager(ProgressionDefinitionProvider definitionProvider,
                     SkillProgressionAccess progressionAccess) {
        this.definitionProvider = definitionProvider;
        this.progressionAccess = progressionAccess;
        instance = this;
    }

    public static SkillTreeManager get() {
        if (instance == null) throw new IllegalStateException("SkillTreeManager not initialized!");
        return instance;
    }

    public boolean canUnlock(@Nonnull UUID uuid, Skill skill) {
        return SkillTreeOperations.evaluateUnlock(uuid, skill, progressionAccess).canUnlock();
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
        SkillUnlockResult result = SkillTreeOperations.unlock(uuid, skill, progressionAccess);
        boolean success = result.unlocked();
        if (success) {
            registerSkillModifiers(uuid, skill);
        }
        return result;
    }

    @Nonnull
    public SkillUnlockResult evaluateUnlock(@Nonnull UUID uuid, @Nonnull Skill skill) {
        return SkillTreeOperations.evaluateUnlock(uuid, skill, progressionAccess);
    }

    public boolean grant(@Nonnull UUID uuid, @Nonnull Skill skill) {
        boolean success = SkillTreeOperations.grant(uuid, skill, progressionAccess);
        if (success) {
            registerSkillModifiers(uuid, skill);
        }
        return success;
    }

    /** Refunds all spent points, clears all unlocked skills, and removes their modifiers. */
    public void resetPlayer(@Nonnull UUID uuid) {
        progressionAccess.resetSkills(uuid);
        ModifierContext.REGISTRY.unregisterByTagPrefix(uuid, "skill:");
    }

    /** Remove all modifier registrations for a player (e.g. on disconnect). */
    public void evictPlayer(@Nonnull UUID uuid) {
        ModifierContext.REGISTRY.unregisterByTagPrefix(uuid, "skill:");
    }

    /**
     * Re-registers all skill modifiers for a player who reconnects.
     * Call this on join once the player's skill data has been loaded.
     */
    public void reloadModifiers(@Nonnull UUID uuid) {
        ModifierContext.REGISTRY.unregisterByTagPrefix(uuid, "skill:");
        for (String skillId : progressionAccess.getUnlockedSkillIds(uuid)) {
            Skill skill = definitionProvider.getSkill(skillId);
            if (skill != null) registerSkillModifiers(uuid, skill);
        }
    }

    // -------------------------------------------------------------------------

    private void registerSkillModifiers(@Nonnull UUID uuid, @Nonnull Skill skill) {
        registerInlineModifiers(uuid, skill.id, skill.modifiers);
        if (skill.passiveId != null && !skill.passiveId.isBlank()) {
            Passive passive = definitionProvider.getPassive(skill.passiveId);
            if (passive != null) {
                registerInlineModifiers(uuid, skill.id, passive.modifiers);
            }
        }
    }

    private void registerInlineModifiers(@Nonnull UUID uuid, @Nonnull String sourceId,
                                         @Nonnull List<InlineModifier> modifiers) {
        var reg = ModifierContext.REGISTRY;
        for (InlineModifier mod : modifiers) {
            if (mod.stat == null) continue;
            String modifierKey = mod.modifierId != null && !mod.modifierId.isBlank() ? mod.modifierId : mod.statId;
            String tagKey = "skill:" + sourceId + ":" + modifierKey;
            ValueModifier<ModifierContext> modifier = buildModifier(mod);
            reg.register(uuid, mod.stat, ModifierTag.of(tagKey), mod.priority, modifier);
        }
    }

    private ValueModifier<ModifierContext> buildModifier(@Nonnull InlineModifier mod) {
        float value = mod.value;
        return switch (mod.operation) {
            case ADD      -> (current, ctx) -> ModifierScopeMatcher.applies(mod, ctx) ? current + value : current;
            case MULTIPLY -> (current, ctx) -> ModifierScopeMatcher.applies(mod, ctx) ? current * value : current;
            case OVERRIDE -> (current, ctx) -> ModifierScopeMatcher.applies(mod, ctx) ? value : current;
        };
    }

}
