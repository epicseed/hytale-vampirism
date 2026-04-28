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

        relicBindings = sanitizeStringMap(relicBindings);
        abilityCooldowns = sanitizeLongMap(abilityCooldowns);
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
}
