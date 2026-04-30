package com.epicseed.vampirism.domain.player;

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
        skillPoints = Math.max(0, skillPoints);
        totalSpent = Math.max(0, totalSpent);
        blood = Math.max(0, blood);
        completedNightHunts = Math.max(0, completedNightHunts);
        nightHuntCooldownMs = Math.max(0L, nightHuntCooldownMs);
        infectionExpiresAtMs = Math.max(0L, infectionExpiresAtMs);
        if (infectionExpiresAtMs > 0L && infectionExpiresAtMs <= System.currentTimeMillis()) {
            infectionExpiresAtMs = 0L;
        }

        if (unlockedSkills == null) {
            unlockedSkills = ConcurrentHashMap.newKeySet();
        } else if (!(unlockedSkills instanceof java.util.concurrent.ConcurrentHashMap.KeySetView<?, ?>)) {
            Set<String> copy = ConcurrentHashMap.newKeySet();
            copy.addAll(unlockedSkills);
            unlockedSkills = copy;
        }

        activeRelicPreset = Math.max(0, activeRelicPreset);
        relicBindings = sanitizeStringMap(relicBindings);
        relicPresets = sanitizeNestedStringMap(relicPresets);
        if (relicPresets.isEmpty() && !relicBindings.isEmpty()) {
            relicPresets.put(relicPresetKey(0), new LinkedHashMap<>(relicBindings));
        }
        relicPresets.computeIfAbsent(relicPresetKey(activeRelicPreset), ignored -> new LinkedHashMap<>());
        relicBindings = new LinkedHashMap<>(relicBindingsFor(activeRelicPreset));
        abilityCooldowns = sanitizeLongMap(abilityCooldowns);
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

    private static LinkedHashMap<String, Long> sanitizeLongMap(Map<String, Long> input) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        if (input == null) {
            return result;
        }
        input.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && value > 0L) {
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
