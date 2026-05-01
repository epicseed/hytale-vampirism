package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.player.PlayerProgressStore;
import com.epicseed.vampirism.domain.player.PlayerVampireProfile;
import com.epicseed.vampirism.domain.player.PlayerVampireProfileRepository;
import com.epicseed.vampirism.domain.player.VampirePlayerStateStore;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Progression and relic-binding facade for vampirism player data.
 * Handles skill points, unlocked skills, and relic preset bindings backed
 * by EpicCore's {@link PlayerProgressStore}.
 *
 * <p>Vampirism-specific persisted state (blood, infection, night hunt cooldowns)
 * lives in {@link com.epicseed.vampirism.domain.player.VampirePlayerStateStore},
 * which is initialized alongside this registry and shares the same underlying store.</p>
 *
 * <p>Each player's data is stored in its own PlayerSkills/{uuid}.json file.</p>
 */
public class PlayerSkillRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static PlayerSkillRegistry instance;

    private final PlayerVampireProfileRepository repository;
    private final PlayerProgressStore<PlayerVampireProfile> progressStore;

    private PlayerSkillRegistry(@Nonnull Path dataDirectory) {
        this.repository = new PlayerVampireProfileRepository(dataDirectory.resolve("PlayerSkills"));
        this.progressStore = new PlayerProgressStore<>(repository);
    }

    public static void init(@Nonnull Path dataDirectory) {
        instance = new PlayerSkillRegistry(dataDirectory);
        VampirePlayerStateStore.init(instance.progressStore);
        LOGGER.atInfo().log("[PlayerSkillRegistry] Initialized. Per-player data directory: " + instance.repository.profilesDirectory());
    }

    @Nonnull
    public static PlayerSkillRegistry get() {
        if (instance == null) throw new IllegalStateException("PlayerSkillRegistry not initialized!");
        return instance;
    }

    /** Call when a player connects — preloads their data from disk into cache. */
    public void onPlayerConnect(@Nonnull UUID uuid) {
        progressStore.onPlayerConnect(uuid);
        LOGGER.atInfo().log("[PlayerSkillRegistry] Loaded data for " + uuid);
    }

    /** Call when a player disconnects — persists their data and removes from cache. */
    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        progressStore.onPlayerDisconnect(uuid);
    }

    public int getSkillPoints(@Nonnull UUID uuid) {
        return progressStore.getSkillPoints(uuid);
    }

    public int getAcquiredSkillPoints(@Nonnull UUID uuid) {
        return progressStore.getAcquiredSkillPoints(uuid);
    }

    public void addSkillPoints(@Nonnull UUID uuid, int amount) {
        progressStore.addSkillPoints(uuid, amount);
    }

    public void setSkillPoints(@Nonnull UUID uuid, int amount) {
        progressStore.setSkillPoints(uuid, amount);
    }

    /**
     * Refunds all skill points spent at purchase time and clears all unlocked skills.
     * Uses the cost stored when each skill was purchased, so tree edits don't affect refunds.
     */
    public void resetSkills(@Nonnull UUID uuid) {
        progressStore.resetSkills(uuid);
    }

    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return progressStore.hasSkill(uuid, skillId);
    }

    @Nonnull
    public Map<String, String> getRelicBindings(@Nonnull UUID uuid) {
        return getRelicBindings(uuid, getActiveRelicPresetIndex(uuid));
    }

    @Nonnull
    public Map<String, String> getRelicBindings(@Nonnull UUID uuid, int presetIndex) {
        return progressStore.getRelicBindings(uuid, presetIndex);
    }

    @Nullable
    public String getRelicBinding(@Nonnull UUID uuid, @Nonnull String slot) {
        return getRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot);
    }

    @Nullable
    public String getRelicBinding(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot) {
        return progressStore.getRelicBinding(uuid, presetIndex, slot);
    }

    public void setRelicBinding(@Nonnull UUID uuid, @Nonnull String slot, @Nonnull String abilityId) {
        setRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot, abilityId);
    }

    public void setRelicBinding(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot, @Nonnull String abilityId) {
        progressStore.setRelicBinding(uuid, presetIndex, slot, abilityId);
    }

    public void setRelicBindings(@Nonnull UUID uuid, @Nonnull Map<String, String> bindings) {
        setRelicBindings(uuid, getActiveRelicPresetIndex(uuid), bindings);
    }

    public void setRelicBindings(@Nonnull UUID uuid, int presetIndex, @Nonnull Map<String, String> bindings) {
        progressStore.setRelicBindings(uuid, presetIndex, bindings);
    }

    public void setRelicBindings(@Nonnull UUID uuid,
                                 @Nonnull Map<Integer, ? extends Map<String, String>> presetBindings,
                                 int activePresetIndex) {
        progressStore.setRelicBindings(uuid, presetBindings, activePresetIndex);
    }

    public void clearRelicBinding(@Nonnull UUID uuid, @Nonnull String slot) {
        clearRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot);
    }

    public void clearRelicBinding(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot) {
        progressStore.clearRelicBinding(uuid, presetIndex, slot);
    }

    public void resetRelicBindings(@Nonnull UUID uuid) {
        resetRelicBindings(uuid, getActiveRelicPresetIndex(uuid));
    }

    public void resetRelicBindings(@Nonnull UUID uuid, int presetIndex) {
        progressStore.resetRelicBindings(uuid, presetIndex);
    }

    public int getActiveRelicPresetIndex(@Nonnull UUID uuid) {
        return progressStore.getActiveRelicPresetIndex(uuid);
    }

    public void setActiveRelicPresetIndex(@Nonnull UUID uuid, int presetIndex) {
        progressStore.setActiveRelicPresetIndex(uuid, presetIndex);
    }

    @Nonnull
    public Map<String, Long> getPersistedAbilityCooldowns(@Nonnull UUID uuid) {
        return progressStore.getPersistedAbilityCooldowns(uuid);
    }

    public void setPersistedAbilityCooldowns(@Nonnull UUID uuid, @Nonnull Map<String, Long> cooldowns) {
        progressStore.setPersistedAbilityCooldowns(uuid, cooldowns);
    }

    /**
     * Atomically checks all preconditions and, if met, deducts the cost and unlocks the skill.
     * Returns true if successfully unlocked; false if already unlocked, insufficient points,
     * or a required skill is missing.
     */
    public boolean tryUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost,
                             @Nonnull Iterable<String> requirementIds) {
        return progressStore.tryUnlock(uuid, skillId, cost, requirementIds);
    }

    public boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return progressStore.grantSkill(uuid, skillId);
    }

    /** Non-mutating check — safe to call for UI display. */
    public boolean canUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost,
                             @Nonnull Iterable<String> requirementIds) {
        return progressStore.canUnlock(uuid, skillId, cost, requirementIds);
    }

    @Nonnull
    public Set<String> getUnlockedSkills(@Nonnull UUID uuid) {
        return progressStore.getUnlockedSkills(uuid);
    }
}
