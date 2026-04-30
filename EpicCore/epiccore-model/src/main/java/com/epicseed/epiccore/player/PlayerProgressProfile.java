package com.epicseed.epiccore.player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared shape for player progression data that can be reused by multiple mods.
 *
 * <p>This class intentionally avoids serialization annotations for now so each mod
 * can preserve its own storage format while the extraction is still in progress.
 */
public class PlayerProgressProfile {

    public int skillPoints = 0;
    public int totalSpent = 0;
    public Set<String> unlockedSkills = ConcurrentHashMap.newKeySet();
    public LinkedHashMap<String, Long> abilityCooldowns = new LinkedHashMap<>();

    public void sanitize() {
        skillPoints = Math.max(0, skillPoints);
        totalSpent = Math.max(0, totalSpent);

        if (unlockedSkills == null) {
            unlockedSkills = ConcurrentHashMap.newKeySet();
        } else if (!(unlockedSkills instanceof java.util.concurrent.ConcurrentHashMap.KeySetView<?, ?>)) {
            Set<String> copy = ConcurrentHashMap.newKeySet();
            copy.addAll(unlockedSkills);
            unlockedSkills = copy;
        }

        abilityCooldowns = sanitizeLongMap(abilityCooldowns);
    }

    public static LinkedHashMap<String, Long> sanitizeLongMap(Map<String, Long> input) {
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
