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
    public LinkedHashMap<String, String> relicBindings = new LinkedHashMap<>();
    public int activeRelicPreset = 0;
    public LinkedHashMap<String, LinkedHashMap<String, String>> relicPresets = new LinkedHashMap<>();
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

    public static LinkedHashMap<String, String> sanitizeStringMap(Map<String, String> input) {
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

    public static LinkedHashMap<String, LinkedHashMap<String, String>> sanitizeNestedStringMap(
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
