package com.epicseed.vampirism.domain.relic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.progression.AbilityCooldownAccess;
import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.RelicBindingDefaults;
import com.epicseed.epiccore.skill.progression.RelicBindingOperations;
import com.epicseed.epiccore.skill.progression.RelicBindingStore;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.runtime.AbilitySlotBindings;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.CooldownTrackerAbilityCooldownAccess;
import com.epicseed.vampirism.skill.runtime.PlayerRegistrySkillProgressionAccess;
import com.epicseed.vampirism.skill.runtime.VampirismProgressionDefinitionProvider;
import com.epicseed.vampirism.systems.VampireInfectionSystem;

public final class RelicBindingService {

    public static final String[] SLOT_KEYS = { "primary", "secondary", "ability1", "ability2", "ability3" };
    public static final int DEFAULT_UTILITY_PRESET_COUNT = 4;
    public static final int DEFAULT_PRESET_COUNT = DEFAULT_UTILITY_PRESET_COUNT + 1;
    private static final RelicBindingStore STORE = PlayerRelicBindingsStore.instance();
    private static final AbilityCooldownAccess COOLDOWNS = CooldownTrackerAbilityCooldownAccess.instance();
    private static final SkillProgressionAccess PROGRESSION = PlayerRegistrySkillProgressionAccess.instance();
    private static final ProgressionDefinitionProvider DEFINITIONS = VampirismProgressionDefinitionProvider.instance();
    private static final RelicBindingDefaults DEFAULTS = new RelicBindingDefaults() {
        private final List<String> slotKeys = List.copyOf(Arrays.asList(SLOT_KEYS));

        @Override
        @Nonnull
        public List<String> slotKeys() {
            return slotKeys;
        }

        @Override
        public String defaultAbilityId(@Nonnull String slot) {
            return AbilitySlotBindings.abilityFor(slot);
        }
    };

    private RelicBindingService() {
    }

    @Nonnull
    public static LinkedHashMap<String, String> getEffectiveBindings(@Nonnull UUID uuid) {
        return getEffectiveBindings(uuid, activePresetIndex(uuid));
    }

    @Nonnull
    public static LinkedHashMap<String, String> getEffectiveBindings(@Nonnull UUID uuid, int presetIndex) {
        return RelicBindingOperations.getEffectiveBindings(uuid, presetIndex, STORE, DEFAULTS);
    }

    @Nonnull
    public static Optional<String> resolveAbilityForSlot(@Nonnull UUID uuid, @Nonnull String slot) {
        return resolveAbilityForSlot(uuid, activePresetIndex(uuid), slot);
    }

    @Nonnull
    public static Optional<String> resolveAbilityForSlot(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot) {
        return RelicBindingOperations.resolveAbilityForSlot(uuid, presetIndex, slot, STORE, DEFAULTS);
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
        STORE.setAll(uuid, presetIndex, RelicBindingOperations.sanitizeBindings(pending, DEFAULTS));
    }

    public static void applyAllBindings(@Nonnull UUID uuid,
                                        @Nonnull Map<Integer, ? extends Map<String, String>> pendingByPreset,
                                        int activePresetIndex) {
        RelicBindingOperations.applyAllBindings(uuid, pendingByPreset, activePresetIndex, STORE, DEFAULTS);
    }

    public static int activePresetIndex(@Nonnull UUID uuid) {
        return STORE.activePresetIndex(uuid);
    }

    public static void setActivePreset(@Nonnull UUID uuid, int presetIndex) {
        STORE.setActivePreset(uuid, presetIndex);
    }

    @Nonnull
    public static List<Skill> listBindableAbilitySkills(@Nonnull UUID uuid) {
        return new ArrayList<>(RelicBindingOperations.listBindableAbilitySkills(uuid, DEFINITIONS, PROGRESSION));
    }

    public static long remainingMs(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return COOLDOWNS.remainingMs(uuid, abilityId);
    }

    public static long slotBindingRemainingMs(@Nonnull UUID uuid,
                                              @Nonnull Map<String, String> savedState,
                                              @Nonnull String slot) {
        return RelicBindingOperations.slotBindingRemainingMs(uuid, savedState, slot, COOLDOWNS);
    }

    public static boolean isAbilityOnCooldown(@Nonnull UUID uuid, @Nullable String abilityId) {
        return RelicBindingOperations.isAbilityOnCooldown(uuid, abilityId, COOLDOWNS);
    }

    @Nullable
    public static String validatePendingBindings(@Nonnull UUID uuid,
                                                 @Nonnull Map<String, String> savedState,
                                                 @Nonnull Map<String, String> pending) {
        return RelicBindingOperations.validatePendingBindings(
                uuid,
                savedState,
                pending,
                DEFAULTS,
                COOLDOWNS,
                RelicBindingService::abilityLabel);
    }

    @Nonnull
    public static String slotCooldownMessage(@Nonnull UUID uuid,
                                             @Nonnull Map<String, String> savedState,
                                             @Nonnull String slot) {
        return RelicBindingOperations.slotCooldownMessage(uuid, savedState, slot, COOLDOWNS, RelicBindingService::abilityLabel);
    }

    @Nonnull
    public static String abilityCooldownMessage(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return RelicBindingOperations.abilityCooldownMessage(uuid, abilityId, COOLDOWNS, RelicBindingService::abilityLabel);
    }

    @Nonnull
    public static String abilityLabel(@Nonnull String abilityId) {
        return RelicBindingOperations.abilityLabel(abilityId, DEFINITIONS);
    }

    @Nonnull
    public static String abilityLabel(@Nonnull String abilityId, Skill owner) {
        return RelicBindingOperations.abilityLabel(abilityId, owner, DEFINITIONS);
    }

    @Nonnull
    public static Optional<String> normalized(String abilityId) {
        return RelicBindingOperations.normalized(abilityId);
    }

    @Nonnull
    public static String formatCooldown(long remainingMs) {
        return RelicBindingOperations.formatCooldown(remainingMs);
    }

    @Nonnull
    public static String slotLabel(@Nonnull String slot) {
        return RelicBindingOperations.slotLabel(slot);
    }

    @Nonnull
    public static String presetLabel(int presetIndex) {
        return presetLabel(presetIndex, DEFAULT_UTILITY_PRESET_COUNT);
    }

    @Nonnull
    public static String presetLabel(int presetIndex, int utilityPresetCount) {
        return RelicBindingOperations.presetLabel(presetIndex, utilityPresetCount);
    }

    @Nonnull
    public static String presetSubtitle(int presetIndex, int utilityPresetCount) {
        return RelicBindingOperations.presetSubtitle(presetIndex, utilityPresetCount);
    }

    public static int clampPresetIndex(int presetIndex, int presetCount) {
        return RelicBindingOperations.clampPresetIndex(presetIndex, presetCount);
    }

    public static int inactiveOffhandPresetIndex(int utilityPresetCount) {
        return RelicBindingOperations.inactiveOffhandPresetIndex(utilityPresetCount);
    }

    public static int presetIndexForUtilitySelection(int selectedSlot, int inactiveSlotIndex, int utilityPresetCount) {
        return RelicBindingOperations.presetIndexForUtilitySelection(selectedSlot, inactiveSlotIndex, utilityPresetCount);
    }

    public static boolean isInactiveOffhandPreset(int presetIndex, int utilityPresetCount) {
        return RelicBindingOperations.isInactiveOffhandPreset(presetIndex, utilityPresetCount);
    }

    private static Skill findSkillByAbilityId(@Nonnull String abilityId) {
        return DEFINITIONS.findSkillByAbilityId(abilityId);
    }
}
