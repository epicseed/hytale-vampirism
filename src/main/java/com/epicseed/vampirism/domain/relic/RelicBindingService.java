package com.epicseed.vampirism.domain.relic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.registry.PlayerRelicBindings;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.RelicBindings;
import com.epicseed.vampirism.systems.VampireInfectionSystem;

public final class RelicBindingService {

    public static final String[] SLOT_KEYS = { "primary", "secondary", "ability1", "ability2", "ability3" };
    public static final int DEFAULT_UTILITY_PRESET_COUNT = 4;
    public static final int DEFAULT_PRESET_COUNT = DEFAULT_UTILITY_PRESET_COUNT + 1;

    private RelicBindingService() {
    }

    @Nonnull
    public static LinkedHashMap<String, String> getEffectiveBindings(@Nonnull UUID uuid) {
        return getEffectiveBindings(uuid, activePresetIndex(uuid));
    }

    @Nonnull
    public static LinkedHashMap<String, String> getEffectiveBindings(@Nonnull UUID uuid, int presetIndex) {
        Map<String, String> overrides = PlayerRelicBindings.get().bindingsFor(uuid, presetIndex);
        LinkedHashMap<String, String> effective = new LinkedHashMap<>();
        for (String slot : SLOT_KEYS) {
            String abilityId;
            if (overrides.containsKey(slot)) {
                abilityId = normalized(overrides.get(slot)).orElse(null);
            } else {
                abilityId = normalized(RelicBindings.abilityFor(slot)).orElse(null);
            }
            if (abilityId != null) {
                effective.put(slot, abilityId);
            }
        }
        return effective;
    }

    @Nonnull
    public static Optional<String> resolveAbilityForSlot(@Nonnull UUID uuid, @Nonnull String slot) {
        return resolveAbilityForSlot(uuid, activePresetIndex(uuid), slot);
    }

    @Nonnull
    public static Optional<String> resolveAbilityForSlot(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot) {
        Map<String, String> overrides = PlayerRelicBindings.get().bindingsFor(uuid, presetIndex);
        if (overrides.containsKey(slot)) {
            return normalized(overrides.get(slot));
        }
        return normalized(RelicBindings.abilityFor(slot));
    }

    @Nonnull
    public static Optional<String> resolveActivationAbility(@Nonnull UUID uuid, @Nonnull String slot) {
        Optional<String> abilityId = resolveAbilityForSlot(uuid, slot);
        if (abilityId.isPresent()) {
            return abilityId;
        }
        if (PlayerSkillRegistry.get().isInfected(uuid)) {
            return Optional.of(VampireInfectionSystem.BLOOD_SUCKER_ABILITY_ID);
        }
        return Optional.empty();
    }

    public static void applyBindings(@Nonnull UUID uuid, @Nonnull Map<String, String> pending) {
        applyBindings(uuid, activePresetIndex(uuid), pending);
    }

    public static void applyBindings(@Nonnull UUID uuid, int presetIndex, @Nonnull Map<String, String> pending) {
        LinkedHashMap<String, String> bindingsToSave = new LinkedHashMap<>();
        for (String slot : SLOT_KEYS) {
            bindingsToSave.put(slot, normalized(pending.get(slot)).orElse(""));
        }
        PlayerRelicBindings.get().setAll(uuid, presetIndex, bindingsToSave);
    }

    public static void applyAllBindings(@Nonnull UUID uuid,
                                        @Nonnull Map<Integer, ? extends Map<String, String>> pendingByPreset,
                                        int activePresetIndex) {
        LinkedHashMap<Integer, LinkedHashMap<String, String>> normalizedByPreset = new LinkedHashMap<>();
        pendingByPreset.forEach((presetIndex, pending) -> {
            if (presetIndex == null || pending == null) {
                return;
            }
            LinkedHashMap<String, String> bindingsToSave = new LinkedHashMap<>();
            for (String slot : SLOT_KEYS) {
                bindingsToSave.put(slot, normalized(pending.get(slot)).orElse(""));
            }
            normalizedByPreset.put(presetIndex, bindingsToSave);
        });
        PlayerRelicBindings.get().setAll(uuid, normalizedByPreset, activePresetIndex);
    }

    public static int activePresetIndex(@Nonnull UUID uuid) {
        return PlayerRelicBindings.get().activePresetIndex(uuid);
    }

    public static void setActivePreset(@Nonnull UUID uuid, int presetIndex) {
        PlayerRelicBindings.get().setActivePreset(uuid, presetIndex);
    }

    @Nonnull
    public static List<Skill> listBindableAbilitySkills(@Nonnull UUID uuid) {
        SkillRegistry registry = Vampirism.getInstance().GetSkillRegistry();
        List<Skill> out = new ArrayList<>();
        for (Skill skill : registry.GetAll()) {
            if (skill.abilityId == null || skill.abilityId.isBlank()) {
                continue;
            }
            if (!PlayerSkillRegistry.get().hasSkill(uuid, skill.id)) {
                continue;
            }
            out.add(skill);
        }
        return out;
    }

    public static long remainingMs(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return AbilityCooldownTracker.getRemainingMs(uuid, abilityId);
    }

    public static long slotBindingRemainingMs(@Nonnull UUID uuid,
                                              @Nonnull Map<String, String> savedState,
                                              @Nonnull String slot) {
        return normalized(savedState.get(slot))
                .map(abilityId -> AbilityCooldownTracker.getRemainingMs(uuid, abilityId))
                .orElse(0L);
    }

    public static boolean isAbilityOnCooldown(@Nonnull UUID uuid, @Nullable String abilityId) {
        return normalized(abilityId)
                .map(id -> AbilityCooldownTracker.getRemainingMs(uuid, id) > 0L)
                .orElse(false);
    }

    @Nullable
    public static String validatePendingBindings(@Nonnull UUID uuid,
                                                 @Nonnull Map<String, String> savedState,
                                                 @Nonnull Map<String, String> pending) {
        for (String slot : SLOT_KEYS) {
            String pendingAbilityId = normalized(pending.get(slot)).orElse(null);
            String savedAbilityId = normalized(savedState.get(slot)).orElse(null);
            if (java.util.Objects.equals(pendingAbilityId, savedAbilityId)) {
                continue;
            }
            if (slotBindingRemainingMs(uuid, savedState, slot) > 0L) {
                return slotCooldownMessage(uuid, savedState, slot);
            }
            if (pendingAbilityId != null && isAbilityOnCooldown(uuid, pendingAbilityId)) {
                return abilityCooldownMessage(uuid, pendingAbilityId);
            }
        }
        return null;
    }

    @Nonnull
    public static String slotCooldownMessage(@Nonnull UUID uuid,
                                             @Nonnull Map<String, String> savedState,
                                             @Nonnull String slot) {
        String abilityId = normalized(savedState.get(slot)).orElse(null);
        if (abilityId == null) {
            return "Slot '" + slotLabel(slot) + "' is locked by cooldown.";
        }
        long remainingMs = AbilityCooldownTracker.getRemainingMs(uuid, abilityId);
        return "Cannot change '" + slotLabel(slot) + "' while '" + abilityLabel(abilityId)
                + "' is on cooldown (" + formatCooldown(remainingMs) + "s remaining).";
    }

    @Nonnull
    public static String abilityCooldownMessage(@Nonnull UUID uuid, @Nonnull String abilityId) {
        long remainingMs = AbilityCooldownTracker.getRemainingMs(uuid, abilityId);
        return "Cannot rebind '" + abilityLabel(abilityId) + "' while it is on cooldown ("
                + formatCooldown(remainingMs) + "s remaining).";
    }

    @Nonnull
    public static String abilityLabel(@Nonnull String abilityId) {
        return abilityLabel(abilityId, findSkillByAbilityId(abilityId));
    }

    @Nonnull
    public static String abilityLabel(@Nonnull String abilityId, Skill owner) {
        Ability ability = Vampirism.getInstance().GetAbilityRegistry().Get(abilityId);
        return (ability != null && ability.displayName != null) ? ability.displayName
                : (owner != null ? owner.displayName : abilityId);
    }

    @Nonnull
    public static Optional<String> normalized(String abilityId) {
        return abilityId == null || abilityId.isBlank() ? Optional.empty() : Optional.of(abilityId);
    }

    @Nonnull
    public static String formatCooldown(long remainingMs) {
        return Long.toString(Math.max(1L, (long) Math.ceil(remainingMs / 1000.0)));
    }

    @Nonnull
    public static String slotLabel(@Nonnull String slot) {
        return switch (slot) {
            case "primary" -> "Primary";
            case "secondary" -> "Secondary";
            case "ability1" -> "Ability 1";
            case "ability2" -> "Ability 2";
            case "ability3" -> "Ability 3";
            default -> slot;
        };
    }

    @Nonnull
    public static String presetLabel(int presetIndex) {
        return presetLabel(presetIndex, DEFAULT_UTILITY_PRESET_COUNT);
    }

    @Nonnull
    public static String presetLabel(int presetIndex, int utilityPresetCount) {
        if (isInactiveOffhandPreset(presetIndex, utilityPresetCount)) {
            return "No Offhand";
        }
        return "Preset " + (Math.max(0, presetIndex) + 1);
    }

    @Nonnull
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

    public static int presetIndexForUtilitySelection(int selectedSlot, int inactiveSlotIndex, int utilityPresetCount) {
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

    private static Skill findSkillByAbilityId(@Nonnull String abilityId) {
        SkillRegistry registry = Vampirism.getInstance().GetSkillRegistry();
        for (Skill skill : registry.GetAll()) {
            if (abilityId.equals(skill.abilityId)) {
                return skill;
            }
        }
        return null;
    }
}
