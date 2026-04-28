package com.epicseed.vampirism.skill.registry;

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
        return getOrLoad(uuid).skillPoints;
    }

    public int getAcquiredSkillPoints(@Nonnull UUID uuid) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return Math.max(0, profile.skillPoints + profile.totalSpent);
        }
    }

    public void addSkillPoints(@Nonnull UUID uuid, int amount) {
        mutateAndSave(uuid, profile -> profile.skillPoints = Math.max(0, profile.skillPoints + amount));
    }

    public void setSkillPoints(@Nonnull UUID uuid, int amount) {
        mutateAndSave(uuid, profile -> profile.skillPoints = Math.max(0, amount));
    }

    /**
     * Refunds all skill points spent at purchase time and clears all unlocked skills.
     * Uses the cost stored when each skill was purchased, so tree edits don't affect refunds.
     */
    public void resetSkills(@Nonnull UUID uuid) {
        mutateAndSave(uuid, profile -> {
            profile.skillPoints = Math.max(0, profile.skillPoints + profile.totalSpent);
            profile.totalSpent = 0;
            profile.unlockedSkills.clear();
        });
    }

    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return getOrLoad(uuid).unlockedSkills.contains(skillId);
    }

    @Nonnull
    public Map<String, String> getRelicBindings(@Nonnull UUID uuid) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return new LinkedHashMap<>(profile.relicBindings);
        }
    }

    @Nullable
    public String getRelicBinding(@Nonnull UUID uuid, @Nonnull String slot) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return profile.relicBindings.get(slot);
        }
    }

    public void setRelicBinding(@Nonnull UUID uuid, @Nonnull String slot, @Nonnull String abilityId) {
        mutateAndSave(uuid, profile -> profile.relicBindings.put(slot, abilityId));
    }

    public void setRelicBindings(@Nonnull UUID uuid, @Nonnull Map<String, String> bindings) {
        mutateAndSave(uuid, profile -> {
            profile.relicBindings.clear();
            bindings.forEach((slot, abilityId) -> {
                if (slot != null && !slot.isBlank() && abilityId != null) {
                    profile.relicBindings.put(slot, abilityId);
                }
            });
        });
    }

    public void clearRelicBinding(@Nonnull UUID uuid, @Nonnull String slot) {
        mutateAndSave(uuid, profile -> profile.relicBindings.remove(slot));
    }

    public void resetRelicBindings(@Nonnull UUID uuid) {
        mutateAndSave(uuid, profile -> profile.relicBindings.clear());
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
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return new LinkedHashMap<>(profile.abilityCooldowns);
        }
    }

    public void setPersistedAbilityCooldowns(@Nonnull UUID uuid, @Nonnull Map<String, Long> cooldowns) {
        mutate(uuid, profile -> {
            profile.abilityCooldowns.clear();
            cooldowns.forEach((abilityId, remainingMs) -> {
                if (abilityId == null || abilityId.isBlank() || remainingMs == null || remainingMs <= 0L) {
                    return;
                }
                profile.abilityCooldowns.put(abilityId, remainingMs);
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
            if (profile.unlockedSkills.contains(skillId)) return false;
            if (profile.skillPoints < cost) return false;
            for (String req : requirementIds) {
                if (!profile.unlockedSkills.contains(req)) return false;
            }
            profile.skillPoints -= cost;
            profile.totalSpent += cost;
            profile.unlockedSkills.add(skillId);
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
            if (profile.unlockedSkills.contains(skillId)) return false;
            profile.unlockedSkills.add(skillId);
        }
        repository.save(uuid, profile);
        evictIfOffline(uuid);
        return true;
    }

    /** Non-mutating check — safe to call for UI display. */
    public boolean canUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost,
                             @Nonnull Iterable<String> requirementIds) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            if (profile.unlockedSkills.contains(skillId)) return false;
            if (profile.skillPoints < cost) return false;
            for (String req : requirementIds) {
                if (!profile.unlockedSkills.contains(req)) return false;
            }
        }
        return true;
    }

    @Nonnull
    public Set<String> getUnlockedSkills(@Nonnull UUID uuid) {
        PlayerVampireProfile profile = getOrLoad(uuid);
        synchronized (profile) {
            return new HashSet<>(profile.unlockedSkills);
        }
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
