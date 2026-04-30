package com.epicseed.vampirism.domain.player;

import com.epicseed.epiccore.player.PlayerProgressProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerVampireProfile {

    @JsonProperty("points")
    public int skillPoints = 0;

    @JsonProperty("spent")
    public int totalSpent = 0;

    @JsonProperty("skills")
    public Set<String> unlockedSkills = ConcurrentHashMap.newKeySet();

    @JsonProperty("relicBindings")
    public LinkedHashMap<String, String> relicBindings = new LinkedHashMap<>();

    @JsonProperty("activeRelicPreset")
    public int activeRelicPreset = 0;

    @JsonProperty("relicPresets")
    public LinkedHashMap<String, LinkedHashMap<String, String>> relicPresets = new LinkedHashMap<>();

    @JsonProperty("blood")
    public int blood = 100;

    @JsonProperty("completedNightHunts")
    public int completedNightHunts = 0;

    @JsonProperty("nightHuntCooldownMs")
    public long nightHuntCooldownMs = -1L;

    @JsonProperty("infectionExpiresAtMs")
    public long infectionExpiresAtMs = 0L;

    @JsonProperty("abilityCooldowns")
    public LinkedHashMap<String, Long> abilityCooldowns = new LinkedHashMap<>();

    public void sanitize() {
        PlayerProgressProfile progress = progressProfile();
        progress.sanitize();
        applyProgressProfile(progress);
        blood = Math.max(0, blood);
        completedNightHunts = Math.max(0, completedNightHunts);
        nightHuntCooldownMs = Math.max(0L, nightHuntCooldownMs);
        infectionExpiresAtMs = Math.max(0L, infectionExpiresAtMs);
        if (infectionExpiresAtMs > 0L && infectionExpiresAtMs <= System.currentTimeMillis()) {
            infectionExpiresAtMs = 0L;
        }

        activeRelicPreset = Math.max(0, activeRelicPreset);
        relicBindings = sanitizeStringMap(relicBindings);
        relicPresets = sanitizeNestedStringMap(relicPresets);
        if (relicPresets.isEmpty() && !relicBindings.isEmpty()) {
            relicPresets.put(relicPresetKey(0), new LinkedHashMap<>(relicBindings));
        }
        relicPresets.computeIfAbsent(relicPresetKey(activeRelicPreset), ignored -> new LinkedHashMap<>());
        relicBindings = new LinkedHashMap<>(relicBindingsFor(activeRelicPreset));
    }

    public PlayerProgressProfile progressProfile() {
        PlayerProgressProfile progress = new PlayerProgressProfile();
        progress.skillPoints = skillPoints;
        progress.totalSpent = totalSpent;
        if (unlockedSkills != null) {
            progress.unlockedSkills.addAll(unlockedSkills);
        }
        progress.abilityCooldowns = new LinkedHashMap<>(abilityCooldowns != null ? abilityCooldowns : Map.of());
        return progress;
    }

    public void applyProgressProfile(PlayerProgressProfile progress) {
        if (progress == null) {
            skillPoints = 0;
            totalSpent = 0;
            unlockedSkills = ConcurrentHashMap.newKeySet();
            abilityCooldowns = new LinkedHashMap<>();
            return;
        }
        skillPoints = Math.max(0, progress.skillPoints);
        totalSpent = Math.max(0, progress.totalSpent);

        Set<String> unlockedCopy = ConcurrentHashMap.newKeySet();
        if (progress.unlockedSkills != null) {
            unlockedCopy.addAll(progress.unlockedSkills);
        }
        unlockedSkills = unlockedCopy;
        abilityCooldowns = new LinkedHashMap<>(progress.abilityCooldowns != null ? progress.abilityCooldowns : Map.of());
    }

    public LinkedHashMap<String, String> activeRelicBindings() {
        return relicBindingsFor(activeRelicPreset);
    }

    public LinkedHashMap<String, String> relicBindingsFor(int presetIndex) {
        if (relicPresets == null) {
            relicPresets = new LinkedHashMap<>();
        }
        return relicPresets.computeIfAbsent(relicPresetKey(presetIndex), ignored -> new LinkedHashMap<>());
    }

    public static String relicPresetKey(int presetIndex) {
        return Integer.toString(Math.max(0, presetIndex));
    }

    private static LinkedHashMap<String, String> sanitizeStringMap(Map<String, String> input) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (input == null) {
            return result;
        }
        input.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null) {
                result.put(key, value);
            }
        });
        return result;
    }

    private static LinkedHashMap<String, LinkedHashMap<String, String>> sanitizeNestedStringMap(
            Map<String, ? extends Map<String, String>> input) {
        LinkedHashMap<String, LinkedHashMap<String, String>> result = new LinkedHashMap<>();
        if (input == null) {
            return result;
        }
        input.forEach((presetKey, bindings) -> {
            if (presetKey == null || presetKey.isBlank()) {
                return;
            }
            result.put(presetKey, sanitizeStringMap(bindings));
        });
        return result;
    }
}
