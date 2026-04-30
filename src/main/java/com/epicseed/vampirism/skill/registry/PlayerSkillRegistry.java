package com.epicseed.vampirism.skill.registry;

import com.epicseed.epiccore.player.PlayerProgressProfile;
import com.epicseed.vampirism.domain.player.PlayerVampireProfile;
import com.epicseed.vampirism.domain.player.PlayerVampireProfileRepository;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Compatibility facade for player vampirism profile data.
 * Each player's data is stored in its own PlayerSkills/{uuid}.json file.
 */
public class PlayerSkillRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static PlayerSkillRegistry instance;

    private final PlayerVampireProfileRepository repository;
    private final ConcurrentHashMap<UUID, PlayerVampireProfile> cache = new ConcurrentHashMap<>();
    /** UUIDs of players currently connected — used to evict offline players from cache after admin ops. */
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    private PlayerSkillRegistry(@Nonnull Path dataDirectory) {
        this.repository = new PlayerVampireProfileRepository(dataDirectory.resolve("PlayerSkills"));
    }

    public static void init(@Nonnull Path dataDirectory) {
        instance = new PlayerSkillRegistry(dataDirectory);
        LOGGER.atInfo().log("[PlayerSkillRegistry] Initialized. Per-player data directory: " + instance.repository.profilesDirectory());
    }

    @Nonnull
    public static PlayerSkillRegistry get() {
        if (instance == null) throw new IllegalStateException("PlayerSkillRegistry not initialized!");
        return instance;
    }

    /** Call when a player connects — preloads their data from disk into cache. */
    public void onPlayerConnect(@Nonnull UUID uuid) {
        onlinePlayers.add(uuid);
        cache.computeIfAbsent(uuid, repository::load);
        LOGGER.atInfo().log("[PlayerSkillRegistry] Loaded data for " + uuid);
    }

    /** Call when a player disconnects — persists their data and removes from cache. */
    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        onlinePlayers.remove(uuid);
        PlayerVampireProfile profile = cache.get(uuid);
        if (profile != null) {
            // Save BEFORE removing from cache. A concurrent admin operation loading from disk
            // will then find up-to-date data rather than a stale snapshot.
            repository.save(uuid, profile);
            cache.remove(uuid);
        }
    }

    public int getSkillPoints(@Nonnull UUID uuid) {
        return readProgress(uuid, progress -> progress.skillPoints);
    }

    public int getAcquiredSkillPoints(@Nonnull UUID uuid) {
        return readProgress(uuid, progress -> Math.max(0, progress.skillPoints + progress.totalSpent));
    }

    public void addSkillPoints(@Nonnull UUID uuid, int amount) {
        mutateAndSaveProgress(uuid, progress -> progress.skillPoints = Math.max(0, progress.skillPoints + amount));
    }

    public void setSkillPoints(@Nonnull UUID uuid, int amount) {
        mutateAndSaveProgress(uuid, progress -> progress.skillPoints = Math.max(0, amount));
    }

    /**
     * Refunds all skill points spent at purchase time and clears all unlocked skills.
     * Uses the cost stored when each skill was purchased, so tree edits don't affect refunds.
     */
    public void resetSkills(@Nonnull UUID uuid) {
        mutateAndSaveProgress(uuid, progress -> {
            progress.skillPoints = Math.max(0, progress.skillPoints + progress.totalSpent);
            progress.totalSpent = 0;
            progress.unlockedSkills.clear();
        });
    }

    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return readProgress(uuid, progress -> progress.unlockedSkills.contains(skillId));
    }

    @Nonnull
    public Map<String, String> getRelicBindings(@Nonnull UUID uuid) {
        return getRelicBindings(uuid, getActiveRelicPresetIndex(uuid));
    }

    @Nonnull
    public Map<String, String> getRelicBindings(@Nonnull UUID uuid, int presetIndex) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return new LinkedHashMap<>(profile.relicBindingsFor(presetIndex));
        }
    }

    @Nullable
    public String getRelicBinding(@Nonnull UUID uuid, @Nonnull String slot) {
        return getRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot);
    }

    @Nullable
    public String getRelicBinding(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return profile.relicBindingsFor(presetIndex).get(slot);
        }
    }

    public void setRelicBinding(@Nonnull UUID uuid, @Nonnull String slot, @Nonnull String abilityId) {
        setRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot, abilityId);
    }

    public void setRelicBinding(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot, @Nonnull String abilityId) {
        mutateAndSave(uuid, profile -> profile.relicBindingsFor(presetIndex).put(slot, abilityId));
    }

    public void setRelicBindings(@Nonnull UUID uuid, @Nonnull Map<String, String> bindings) {
        setRelicBindings(uuid, getActiveRelicPresetIndex(uuid), bindings);
    }

    public void setRelicBindings(@Nonnull UUID uuid, int presetIndex, @Nonnull Map<String, String> bindings) {
        mutateAndSave(uuid, profile -> {
            Map<String, String> presetBindings = profile.relicBindingsFor(presetIndex);
            presetBindings.clear();
            bindings.forEach((slot, abilityId) -> {
                if (slot != null && !slot.isBlank() && abilityId != null) {
                    presetBindings.put(slot, abilityId);
                }
            });
        });
    }

    public void setRelicBindings(@Nonnull UUID uuid,
                                 @Nonnull Map<Integer, ? extends Map<String, String>> presetBindings,
                                 int activePresetIndex) {
        mutateAndSave(uuid, profile -> {
            profile.relicPresets.clear();
            presetBindings.forEach((presetIndex, bindings) -> {
                if (presetIndex == null || bindings == null) {
                    return;
                }
                LinkedHashMap<String, String> sanitized = new LinkedHashMap<>();
                bindings.forEach((slot, abilityId) -> {
                    if (slot == null || slot.isBlank() || abilityId == null) {
                        return;
                    }
                    sanitized.put(slot, abilityId);
                });
                profile.relicPresets.put(PlayerVampireProfile.relicPresetKey(presetIndex), sanitized);
            });
            profile.activeRelicPreset = Math.max(0, activePresetIndex);
        });
    }

    public void clearRelicBinding(@Nonnull UUID uuid, @Nonnull String slot) {
        clearRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot);
    }

    public void clearRelicBinding(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot) {
        mutateAndSave(uuid, profile -> profile.relicBindingsFor(presetIndex).remove(slot));
    }

    public void resetRelicBindings(@Nonnull UUID uuid) {
        resetRelicBindings(uuid, getActiveRelicPresetIndex(uuid));
    }

    public void resetRelicBindings(@Nonnull UUID uuid, int presetIndex) {
        mutateAndSave(uuid, profile -> profile.relicBindingsFor(presetIndex).clear());
    }

    public int getActiveRelicPresetIndex(@Nonnull UUID uuid) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return Math.max(0, profile.activeRelicPreset);
        }
    }

    public void setActiveRelicPresetIndex(@Nonnull UUID uuid, int presetIndex) {
        mutate(uuid, profile -> profile.activeRelicPreset = Math.max(0, presetIndex));
    }

    public int getPersistedBlood(@Nonnull UUID uuid) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return profile.blood;
        }
    }

    public int getCompletedNightHunts(@Nonnull UUID uuid) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return profile.completedNightHunts;
        }
    }

    public void incrementCompletedNightHunts(@Nonnull UUID uuid) {
        mutateAndSave(uuid, profile -> profile.completedNightHunts = Math.max(0, profile.completedNightHunts + 1));
    }

    public void setPersistedBlood(@Nonnull UUID uuid, int blood) {
        mutate(uuid, profile -> profile.blood = Math.max(0, blood));
    }

    @Nonnull
    public Map<String, Long> getPersistedAbilityCooldowns(@Nonnull UUID uuid) {
        return readProgress(uuid, progress -> new LinkedHashMap<>(progress.abilityCooldowns));
    }

    public void setPersistedAbilityCooldowns(@Nonnull UUID uuid, @Nonnull Map<String, Long> cooldowns) {
        mutateProgress(uuid, progress -> {
            progress.abilityCooldowns.clear();
            cooldowns.forEach((abilityId, remainingMs) -> {
                if (abilityId == null || abilityId.isBlank() || remainingMs == null || remainingMs <= 0L) {
                    return;
                }
                progress.abilityCooldowns.put(abilityId, remainingMs);
            });
        });
    }

    public long getPersistedNightHuntCooldownMs(@Nonnull UUID uuid) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return profile.nightHuntCooldownMs;
        }
    }

    public void setPersistedNightHuntCooldownMs(@Nonnull UUID uuid, long cooldownMs) {
        mutate(uuid, profile -> profile.nightHuntCooldownMs = Math.max(0L, cooldownMs));
    }

    public long getInfectionExpiresAtMs(@Nonnull UUID uuid) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return Math.max(0L, profile.infectionExpiresAtMs);
        }
    }

    public long getInfectionRemainingMs(@Nonnull UUID uuid) {
        return Math.max(0L, getInfectionExpiresAtMs(uuid) - System.currentTimeMillis());
    }

    public boolean isInfected(@Nonnull UUID uuid) {
        return getInfectionRemainingMs(uuid) > 0L;
    }

    public void setInfectionExpiresAtMs(@Nonnull UUID uuid, long expiresAtMs) {
        mutateAndSave(uuid, profile -> profile.infectionExpiresAtMs = Math.max(0L, expiresAtMs));
    }

    public void clearInfection(@Nonnull UUID uuid) {
        setInfectionExpiresAtMs(uuid, 0L);
    }

    /**
     * Atomically checks all preconditions and, if met, deducts the cost and unlocks the skill.
     * Returns true if successfully unlocked; false if already unlocked, insufficient points,
     * or a required skill is missing.
     */
    public boolean tryUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost,
                             @Nonnull Iterable<String> requirementIds) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        boolean success;
        synchronized (profile) {
            PlayerProgressProfile progress = profile.progressProfile();
            if (progress.unlockedSkills.contains(skillId)) return false;
            if (progress.skillPoints < cost) return false;
            for (String req : requirementIds) {
                if (!progress.unlockedSkills.contains(req)) return false;
            }
            progress.skillPoints -= cost;
            progress.totalSpent += cost;
            progress.unlockedSkills.add(skillId);
            progress.sanitize();
            profile.applyProgressProfile(progress);
            success = true;
        }
        if (success) {
            repository.save(uuid, profile);
            evictIfOffline(uuid);
        }
        return success;
    }

    public boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            PlayerProgressProfile progress = profile.progressProfile();
            if (progress.unlockedSkills.contains(skillId)) return false;
            progress.unlockedSkills.add(skillId);
            progress.sanitize();
            profile.applyProgressProfile(progress);
        }
        repository.save(uuid, profile);
        evictIfOffline(uuid);
        return true;
    }

    /** Non-mutating check — safe to call for UI display. */
    public boolean canUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost,
                             @Nonnull Iterable<String> requirementIds) {
        return readProgress(uuid, progress -> {
            if (progress.unlockedSkills.contains(skillId)) return false;
            if (progress.skillPoints < cost) return false;
            for (String req : requirementIds) {
                if (!progress.unlockedSkills.contains(req)) return false;
            }
            return true;
        });
    }

    @Nonnull
    public Set<String> getUnlockedSkills(@Nonnull UUID uuid) {
        return readProgress(uuid, progress -> new HashSet<>(progress.unlockedSkills));
    }

    public void updateProfile(@Nonnull UUID uuid, @Nonnull Consumer<PlayerVampireProfile> updater) {
        mutateAndSave(uuid, updater);
    }

    /** Evicts cache entry for offline players to prevent memory leaks from admin operations. */
    private void evictIfOffline(@Nonnull UUID uuid) {
        if (!onlinePlayers.contains(uuid)) {
            cache.remove(uuid);
        }
    }

    @Nonnull
    private PlayerVampireProfile getOrLoad(@Nonnull UUID uuid) {
        return cache.computeIfAbsent(uuid, repository::load);
    }

    private <T> T readProgress(@Nonnull UUID uuid, @Nonnull Function<PlayerProgressProfile, T> reader) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return reader.apply(profile.progressProfile());
        }
    }

    private void mutateProgress(@Nonnull UUID uuid, @Nonnull Consumer<PlayerProgressProfile> updater) {
        mutate(uuid, profile -> {
            PlayerProgressProfile progress = profile.progressProfile();
            updater.accept(progress);
            progress.sanitize();
            profile.applyProgressProfile(progress);
        });
    }

    private void mutateAndSaveProgress(@Nonnull UUID uuid, @Nonnull Consumer<PlayerProgressProfile> updater) {
        mutateAndSave(uuid, profile -> {
            PlayerProgressProfile progress = profile.progressProfile();
            updater.accept(progress);
            progress.sanitize();
            profile.applyProgressProfile(progress);
        });
    }

    private void mutate(@Nonnull UUID uuid, @Nonnull Consumer<PlayerVampireProfile> updater) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            updater.accept(profile);
            profile.sanitize();
        }
    }

    private void mutateAndSave(@Nonnull UUID uuid, @Nonnull Consumer<PlayerVampireProfile> updater) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            updater.accept(profile);
            profile.sanitize();
        }
        repository.save(uuid, profile);
        evictIfOffline(uuid);
    }
}
