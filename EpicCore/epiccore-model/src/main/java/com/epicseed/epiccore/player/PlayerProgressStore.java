package com.epicseed.epiccore.player;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

public class PlayerProgressStore<P extends PlayerProgressProfileHost> {

    private final PlayerProfileRepository<P> repository;
    private final ConcurrentHashMap<UUID, P> cache = new ConcurrentHashMap<>();
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    public PlayerProgressStore(PlayerProfileRepository<P> repository) {
        this.repository = repository;
    }

    public void onPlayerConnect(UUID uuid) {
        onlinePlayers.add(uuid);
        cache.computeIfAbsent(uuid, repository::load);
    }

    public void onPlayerDisconnect(UUID uuid) {
        onlinePlayers.remove(uuid);
        P profile = cache.get(uuid);
        if (profile != null) {
            repository.save(uuid, profile);
            cache.remove(uuid);
        }
    }

    public int getSkillPoints(UUID uuid) {
        return readProgress(uuid, progress -> progress.skillPoints);
    }

    public int getAcquiredSkillPoints(UUID uuid) {
        return readProgress(uuid, progress -> Math.max(0, progress.skillPoints + progress.totalSpent));
    }

    public void addSkillPoints(UUID uuid, int amount) {
        mutateAndSaveProgress(uuid, progress -> progress.skillPoints = Math.max(0, progress.skillPoints + amount));
    }

    public void setSkillPoints(UUID uuid, int amount) {
        mutateAndSaveProgress(uuid, progress -> progress.skillPoints = Math.max(0, amount));
    }

    public void resetSkills(UUID uuid) {
        mutateAndSaveProgress(uuid, progress -> {
            progress.skillPoints = Math.max(0, progress.skillPoints + progress.totalSpent);
            progress.totalSpent = 0;
            progress.unlockedSkills.clear();
        });
    }

    public boolean hasSkill(UUID uuid, String skillId) {
        return readProgress(uuid, progress -> progress.unlockedSkills.contains(skillId));
    }

    public Map<String, String> getRelicBindings(UUID uuid) {
        return getRelicBindings(uuid, getActiveRelicPresetIndex(uuid));
    }

    public Map<String, String> getRelicBindings(UUID uuid, int presetIndex) {
        return readProgress(uuid, progress -> new LinkedHashMap<>(progress.relicBindingsFor(presetIndex)));
    }

    public String getRelicBinding(UUID uuid, String slot) {
        return getRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot);
    }

    public String getRelicBinding(UUID uuid, int presetIndex, String slot) {
        return readProgress(uuid, progress -> progress.relicBindingsFor(presetIndex).get(slot));
    }

    public void setRelicBinding(UUID uuid, String slot, String abilityId) {
        setRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot, abilityId);
    }

    public void setRelicBinding(UUID uuid, int presetIndex, String slot, String abilityId) {
        mutateAndSaveProgress(uuid, progress -> progress.relicBindingsFor(presetIndex).put(slot, abilityId));
    }

    public void setRelicBindings(UUID uuid, Map<String, String> bindings) {
        setRelicBindings(uuid, getActiveRelicPresetIndex(uuid), bindings);
    }

    public void setRelicBindings(UUID uuid, int presetIndex, Map<String, String> bindings) {
        mutateAndSaveProgress(uuid, progress -> {
            Map<String, String> presetBindings = progress.relicBindingsFor(presetIndex);
            presetBindings.clear();
            bindings.forEach((slot, abilityId) -> {
                if (slot != null && !slot.isBlank() && abilityId != null) {
                    presetBindings.put(slot, abilityId);
                }
            });
            progress.relicBindings = new LinkedHashMap<>(progress.relicBindingsFor(progress.activeRelicPreset));
        });
    }

    public void setRelicBindings(UUID uuid,
                                 Map<Integer, ? extends Map<String, String>> presetBindings,
                                 int activePresetIndex) {
        mutateAndSaveProgress(uuid, progress -> {
            progress.relicPresets.clear();
            presetBindings.forEach((presetIndex, bindings) -> {
                if (presetIndex == null || bindings == null) {
                    return;
                }
                LinkedHashMap<String, String> sanitized = PlayerProgressProfile.sanitizeStringMap(bindings);
                progress.relicPresets.put(PlayerProgressProfile.relicPresetKey(presetIndex), sanitized);
            });
            progress.activeRelicPreset = Math.max(0, activePresetIndex);
            progress.relicBindings = new LinkedHashMap<>(progress.relicBindingsFor(progress.activeRelicPreset));
        });
    }

    public void clearRelicBinding(UUID uuid, String slot) {
        clearRelicBinding(uuid, getActiveRelicPresetIndex(uuid), slot);
    }

    public void clearRelicBinding(UUID uuid, int presetIndex, String slot) {
        mutateAndSaveProgress(uuid, progress -> progress.relicBindingsFor(presetIndex).remove(slot));
    }

    public void resetRelicBindings(UUID uuid) {
        resetRelicBindings(uuid, getActiveRelicPresetIndex(uuid));
    }

    public void resetRelicBindings(UUID uuid, int presetIndex) {
        mutateAndSaveProgress(uuid, progress -> progress.relicBindingsFor(presetIndex).clear());
    }

    public int getActiveRelicPresetIndex(UUID uuid) {
        return readProgress(uuid, progress -> Math.max(0, progress.activeRelicPreset));
    }

    public void setActiveRelicPresetIndex(UUID uuid, int presetIndex) {
        mutateProgress(uuid, progress -> progress.activeRelicPreset = Math.max(0, presetIndex));
    }

    public Map<String, Long> getPersistedAbilityCooldowns(UUID uuid) {
        return readProgress(uuid, progress -> new LinkedHashMap<>(progress.abilityCooldowns));
    }

    public void setPersistedAbilityCooldowns(UUID uuid, Map<String, Long> cooldowns) {
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

    public boolean tryUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds) {
        P profile = getOrLoad(uuid);
        boolean success;
        synchronized (profile) {
            PlayerProgressProfile progress = profile.progressProfile();
            if (progress.unlockedSkills.contains(skillId) || progress.skillPoints < cost) {
                return false;
            }
            for (String requirementId : requirementIds) {
                if (!progress.unlockedSkills.contains(requirementId)) {
                    return false;
                }
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
        return true;
    }

    public boolean grantSkill(UUID uuid, String skillId) {
        P profile = getOrLoad(uuid);
        synchronized (profile) {
            PlayerProgressProfile progress = profile.progressProfile();
            if (progress.unlockedSkills.contains(skillId)) {
                return false;
            }
            progress.unlockedSkills.add(skillId);
            progress.sanitize();
            profile.applyProgressProfile(progress);
        }
        repository.save(uuid, profile);
        evictIfOffline(uuid);
        return true;
    }

    public boolean canUnlock(UUID uuid, String skillId, int cost, Iterable<String> requirementIds) {
        return readProgress(uuid, progress -> {
            if (progress.unlockedSkills.contains(skillId) || progress.skillPoints < cost) {
                return false;
            }
            for (String requirementId : requirementIds) {
                if (!progress.unlockedSkills.contains(requirementId)) {
                    return false;
                }
            }
            return true;
        });
    }

    public Set<String> getUnlockedSkills(UUID uuid) {
        return readProgress(uuid, progress -> new HashSet<>(progress.unlockedSkills));
    }

    public <T> T readProfile(UUID uuid, Function<P, T> reader) {
        P profile = getOrLoad(uuid);
        synchronized (profile) {
            return reader.apply(profile);
        }
    }

    public void mutateProfile(UUID uuid, Consumer<P> updater) {
        P profile = getOrLoad(uuid);
        synchronized (profile) {
            updater.accept(profile);
            PlayerProgressProfile progress = profile.progressProfile();
            progress.sanitize();
            profile.applyProgressProfile(progress);
        }
    }

    public void mutateAndSaveProfile(UUID uuid, Consumer<P> updater) {
        P profile = getOrLoad(uuid);
        synchronized (profile) {
            updater.accept(profile);
            PlayerProgressProfile progress = profile.progressProfile();
            progress.sanitize();
            profile.applyProgressProfile(progress);
        }
        repository.save(uuid, profile);
        evictIfOffline(uuid);
    }

    private P getOrLoad(UUID uuid) {
        return cache.computeIfAbsent(uuid, repository::load);
    }

    private void evictIfOffline(UUID uuid) {
        if (!onlinePlayers.contains(uuid)) {
            cache.remove(uuid);
        }
    }

    private <T> T readProgress(UUID uuid, Function<PlayerProgressProfile, T> reader) {
        P profile = getOrLoad(uuid);
        synchronized (profile) {
            return reader.apply(profile.progressProfile());
        }
    }

    private void mutateProgress(UUID uuid, Consumer<PlayerProgressProfile> updater) {
        mutateProfile(uuid, profile -> {
            PlayerProgressProfile progress = profile.progressProfile();
            updater.accept(progress);
            progress.sanitize();
            profile.applyProgressProfile(progress);
        });
    }

    private void mutateAndSaveProgress(UUID uuid, Consumer<PlayerProgressProfile> updater) {
        mutateAndSaveProfile(uuid, profile -> {
            PlayerProgressProfile progress = profile.progressProfile();
            updater.accept(progress);
            progress.sanitize();
            profile.applyProgressProfile(progress);
        });
    }
}
