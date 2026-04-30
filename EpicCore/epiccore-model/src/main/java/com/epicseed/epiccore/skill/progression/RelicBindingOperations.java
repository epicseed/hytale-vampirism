package com.epicseed.epiccore.skill.progression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.Skill;

public final class RelicBindingOperations {

    private RelicBindingOperations() {
    }

    public static LinkedHashMap<String, String> getEffectiveBindings(UUID uuid,
                                                                     int presetIndex,
                                                                     RelicBindingStore store,
                                                                     RelicBindingDefaults defaults) {
        return getEffectiveBindings(store.bindingsFor(uuid, presetIndex), defaults);
    }

    public static LinkedHashMap<String, String> getEffectiveBindings(Map<String, String> overrides,
                                                                     RelicBindingDefaults defaults) {
        LinkedHashMap<String, String> effective = new LinkedHashMap<>();
        for (String slot : defaults.slotKeys()) {
            Optional<String> abilityId = resolveAbilityForSlot(overrides, defaults, slot);
            abilityId.ifPresent(id -> effective.put(slot, id));
        }
        return effective;
    }

    public static Optional<String> resolveAbilityForSlot(UUID uuid,
                                                         int presetIndex,
                                                         String slot,
                                                         RelicBindingStore store,
                                                         RelicBindingDefaults defaults) {
        return resolveAbilityForSlot(store.bindingsFor(uuid, presetIndex), defaults, slot);
    }

    public static Optional<String> resolveAbilityForSlot(Map<String, String> overrides,
                                                         RelicBindingDefaults defaults,
                                                         String slot) {
        if (overrides.containsKey(slot)) {
            return normalized(overrides.get(slot));
        }
        return normalized(defaults.defaultAbilityId(slot));
    }

    public static LinkedHashMap<String, String> sanitizeBindings(Map<String, String> pending,
                                                                 RelicBindingDefaults defaults) {
        LinkedHashMap<String, String> bindingsToSave = new LinkedHashMap<>();
        for (String slot : defaults.slotKeys()) {
            bindingsToSave.put(slot, normalized(pending.get(slot)).orElse(""));
        }
        return bindingsToSave;
    }

    public static LinkedHashMap<Integer, LinkedHashMap<String, String>> sanitizeBindingsByPreset(
            Map<Integer, ? extends Map<String, String>> pendingByPreset,
            RelicBindingDefaults defaults) {
        LinkedHashMap<Integer, LinkedHashMap<String, String>> normalizedByPreset = new LinkedHashMap<>();
        pendingByPreset.forEach((presetIndex, pending) -> {
            if (presetIndex == null || pending == null) {
                return;
            }
            normalizedByPreset.put(presetIndex, sanitizeBindings(pending, defaults));
        });
        return normalizedByPreset;
    }

    public static void applyAllBindings(UUID uuid,
                                        Map<Integer, ? extends Map<String, String>> pendingByPreset,
                                        int activePresetIndex,
                                        RelicBindingStore store,
                                        RelicBindingDefaults defaults) {
        store.setAll(uuid, sanitizeBindingsByPreset(pendingByPreset, defaults), activePresetIndex);
    }

    public static List<Skill> listBindableAbilitySkills(UUID uuid,
                                                        ProgressionDefinitionProvider definitions,
                                                        SkillProgressionAccess progression) {
        List<Skill> out = new ArrayList<>();
        for (Skill skill : definitions.getAllSkills()) {
            if (skill.abilityId == null || skill.abilityId.isBlank()) {
                continue;
            }
            if (!progression.hasSkill(uuid, skill.id)) {
                continue;
            }
            out.add(skill);
        }
        return out;
    }

    public static long slotBindingRemainingMs(UUID uuid,
                                              Map<String, String> savedState,
                                              String slot,
                                              AbilityCooldownAccess cooldownAccess) {
        return normalized(savedState.get(slot))
                .map(abilityId -> cooldownAccess.remainingMs(uuid, abilityId))
                .orElse(0L);
    }

    public static boolean isAbilityOnCooldown(UUID uuid,
                                              String abilityId,
                                              AbilityCooldownAccess cooldownAccess) {
        return normalized(abilityId)
                .map(id -> cooldownAccess.remainingMs(uuid, id) > 0L)
                .orElse(false);
    }

    public static String validatePendingBindings(UUID uuid,
                                                 Map<String, String> savedState,
                                                 Map<String, String> pending,
                                                 RelicBindingDefaults defaults,
                                                 AbilityCooldownAccess cooldownAccess,
                                                 Function<String, String> abilityLabelResolver) {
        for (String slot : defaults.slotKeys()) {
            String pendingAbilityId = normalized(pending.get(slot)).orElse(null);
            String savedAbilityId = normalized(savedState.get(slot)).orElse(null);
            if (java.util.Objects.equals(pendingAbilityId, savedAbilityId)) {
                continue;
            }
            if (slotBindingRemainingMs(uuid, savedState, slot, cooldownAccess) > 0L) {
                return slotCooldownMessage(uuid, savedState, slot, cooldownAccess, abilityLabelResolver);
            }
            if (pendingAbilityId != null && isAbilityOnCooldown(uuid, pendingAbilityId, cooldownAccess)) {
                return abilityCooldownMessage(uuid, pendingAbilityId, cooldownAccess, abilityLabelResolver);
            }
        }
        return null;
    }

    public static String slotCooldownMessage(UUID uuid,
                                             Map<String, String> savedState,
                                             String slot,
                                             AbilityCooldownAccess cooldownAccess,
                                             Function<String, String> abilityLabelResolver) {
        String abilityId = normalized(savedState.get(slot)).orElse(null);
        if (abilityId == null) {
            return "Slot '" + slotLabel(slot) + "' is locked by cooldown.";
        }
        long remainingMs = cooldownAccess.remainingMs(uuid, abilityId);
        return "Cannot change '" + slotLabel(slot) + "' while '" + abilityLabelResolver.apply(abilityId)
                + "' is on cooldown (" + formatCooldown(remainingMs) + "s remaining).";
    }

    public static String abilityCooldownMessage(UUID uuid,
                                                String abilityId,
                                                AbilityCooldownAccess cooldownAccess,
                                                Function<String, String> abilityLabelResolver) {
        long remainingMs = cooldownAccess.remainingMs(uuid, abilityId);
        return "Cannot rebind '" + abilityLabelResolver.apply(abilityId) + "' while it is on cooldown ("
                + formatCooldown(remainingMs) + "s remaining).";
    }

    public static String abilityLabel(String abilityId,
                                      ProgressionDefinitionProvider definitions) {
        return abilityLabel(abilityId, definitions.findSkillByAbilityId(abilityId), definitions);
    }

    public static String abilityLabel(String abilityId,
                                      Skill owner,
                                      ProgressionDefinitionProvider definitions) {
        Ability ability = definitions.getAbility(abilityId);
        if (ability != null && ability.displayName != null && !ability.displayName.isBlank()) {
            return ability.displayName;
        }
        if (owner != null && owner.displayName != null && !owner.displayName.isBlank()) {
            return owner.displayName;
        }
        return abilityId;
    }

    public static Optional<String> normalized(String abilityId) {
        return abilityId == null || abilityId.isBlank() ? Optional.empty() : Optional.of(abilityId);
    }

    public static String formatCooldown(long remainingMs) {
        return Long.toString(Math.max(1L, (long) Math.ceil(remainingMs / 1000.0)));
    }

    public static String slotLabel(String slot) {
        return switch (slot) {
            case "primary" -> "Primary";
            case "secondary" -> "Secondary";
            case "ability1" -> "Ability 1";
            case "ability2" -> "Ability 2";
            case "ability3" -> "Ability 3";
            default -> slot;
        };
    }

    public static String presetLabel(int presetIndex) {
        return presetLabel(presetIndex, 4);
    }

    public static String presetLabel(int presetIndex, int utilityPresetCount) {
        if (isInactiveOffhandPreset(presetIndex, utilityPresetCount)) {
            return "No Offhand";
        }
        return "Preset " + (Math.max(0, presetIndex) + 1);
    }

    public static String presetSubtitle(int presetIndex, int utilityPresetCount) {
        if (isInactiveOffhandPreset(presetIndex, utilityPresetCount)) {
            return "Utility inactive";
        }
        return "Utility Slot " + (presetIndex + 1);
    }

    public static int clampPresetIndex(int presetIndex, int presetCount) {
        if (presetCount <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(presetCount - 1, presetIndex));
    }

    public static int inactiveOffhandPresetIndex(int utilityPresetCount) {
        return Math.max(0, utilityPresetCount);
    }

    public static int presetIndexForUtilitySelection(int selectedSlot,
                                                     int inactiveSlotIndex,
                                                     int utilityPresetCount) {
        if (selectedSlot == inactiveSlotIndex) {
            return inactiveOffhandPresetIndex(utilityPresetCount);
        }
        if (selectedSlot >= 0 && selectedSlot < utilityPresetCount) {
            return selectedSlot;
        }
        return -1;
    }

    public static boolean isInactiveOffhandPreset(int presetIndex, int utilityPresetCount) {
        return presetIndex == inactiveOffhandPresetIndex(utilityPresetCount);
    }
}
