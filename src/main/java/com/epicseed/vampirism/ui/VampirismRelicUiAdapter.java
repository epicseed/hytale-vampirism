package com.epicseed.vampirism.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.runtime.AbilitySlotBindings;
import com.epicseed.epiccore.skill.ui.RelicAbilityView;
import com.epicseed.epiccore.skill.ui.RelicUiAdapter;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.domain.relic.RelicBindingService;
import com.epicseed.vampirism.relic.RelicPresetProjectionService;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.vampirism.skill.runtime.VampirismProgressionDefinitionProvider;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampirismRelicUiAdapter implements RelicUiAdapter {

    private static final String RELIC_ITEM_ID = "VampirismRelic";
    private static final VampirismRelicUiAdapter INSTANCE = new VampirismRelicUiAdapter();
    private static final ProgressionDefinitionProvider DEFINITIONS = VampirismProgressionDefinitionProvider.instance();

    private VampirismRelicUiAdapter() {
    }

    public static VampirismRelicUiAdapter instance() {
        return INSTANCE;
    }

    @Override
    public int maxPresetCount() {
        return RelicBindingService.DEFAULT_PRESET_COUNT;
    }

    @Override
    public long cooldownRefreshIntervalMs() {
        return VampirismConfig.get().getCooldownHudUpdateIntervalMs();
    }

    @Override
    public int totalPresetCount(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        return RelicPresetProjectionService.totalPresetCount(ref, store);
    }

    @Override
    public int activePresetIndex(@Nonnull UUID uuid) {
        return RelicBindingService.activePresetIndex(uuid);
    }

    @Override
    public void setActivePreset(@Nonnull UUID uuid, int presetIndex) {
        RelicBindingService.setActivePreset(uuid, presetIndex);
    }

    @Override
    public int clampPresetIndex(int presetIndex, int presetCount) {
        return RelicBindingService.clampPresetIndex(presetIndex, presetCount);
    }

    @Override
    @Nonnull
    public Map<String, String> getEffectiveBindings(@Nonnull UUID uuid, int presetIndex) {
        return RelicBindingService.getEffectiveBindings(uuid, presetIndex);
    }

    @Override
    public void applyAllBindings(@Nonnull UUID uuid,
                                 @Nonnull Map<Integer, ? extends Map<String, String>> pendingByPreset,
                                 int activePresetIndex) {
        RelicBindingService.applyAllBindings(uuid, pendingByPreset, activePresetIndex);
    }

    @Override
    @Nonnull
    public List<RelicAbilityView> listBindableAbilities(@Nonnull UUID uuid) {
        List<Skill> skills = RelicBindingService.listBindableAbilitySkills(uuid);
        List<RelicAbilityView> out = new ArrayList<>(skills.size());
        for (Skill skill : skills) {
            RelicAbilityView view = toAbilityView(skill);
            if (view != null) {
                out.add(view);
            }
        }
        return out;
    }

    @Override
    @Nullable
    public RelicAbilityView describeAbility(@Nullable String abilityId) {
        if (abilityId == null || abilityId.isBlank()) {
            return null;
        }
        Skill skill = DEFINITIONS.findSkillByAbilityId(abilityId);
        if (skill == null) {
            com.epicseed.epiccore.skill.model.Ability ability = DEFINITIONS.getAbility(abilityId);
            if (ability == null) {
                return null;
            }
            String displayName = ability.displayName != null && !ability.displayName.isBlank()
                    ? ability.displayName
                    : abilityId;
            return new RelicAbilityView(abilityId, displayName, ability.description, null, null);
        }
        return toAbilityView(skill);
    }

    @Override
    @Nullable
    public String defaultAbilityId(@Nonnull String slot) {
        return RelicBindingService.normalized(AbilitySlotBindings.abilityFor(slot)).orElse(null);
    }

    @Override
    @Nullable
    public String activeAbilityForSlot(@Nonnull UUID uuid, @Nonnull String slot) {
        return RelicBindingService.resolveAbilityForSlot(uuid, slot).orElse(null);
    }

    @Override
    public long remainingMs(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return RelicBindingService.remainingMs(uuid, abilityId);
    }

    @Override
    public long slotBindingRemainingMs(@Nonnull UUID uuid,
                                       @Nonnull Map<String, String> savedState,
                                       @Nonnull String slot) {
        return RelicBindingService.slotBindingRemainingMs(uuid, savedState, slot);
    }

    @Override
    public boolean isAbilityOnCooldown(@Nonnull UUID uuid, @Nullable String abilityId) {
        return RelicBindingService.isAbilityOnCooldown(uuid, abilityId);
    }

    @Override
    @Nullable
    public String validatePendingBindings(@Nonnull UUID uuid,
                                          @Nonnull Map<String, String> savedState,
                                          @Nonnull Map<String, String> pending) {
        return RelicBindingService.validatePendingBindings(uuid, savedState, pending);
    }

    @Override
    @Nonnull
    public String abilityCooldownMessage(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return RelicBindingService.abilityCooldownMessage(uuid, abilityId);
    }

    @Override
    @Nonnull
    public String slotLabel(@Nonnull String slot) {
        return RelicBindingService.slotLabel(slot);
    }

    @Override
    @Nonnull
    public String slotHint(@Nonnull String slot) {
        return switch (slot) {
            case "primary" -> "Primary";
            case "secondary" -> "Secondary";
            case "ability1" -> "Ability1";
            case "ability2" -> "Ability2";
            case "ability3" -> "Ability3";
            default -> slot;
        };
    }

    @Override
    @Nonnull
    public String presetLabel(int presetIndex, int utilityPresetCount) {
        return RelicBindingService.presetLabel(presetIndex, utilityPresetCount);
    }

    @Override
    @Nonnull
    public String presetSubtitle(int presetIndex, int utilityPresetCount) {
        return RelicBindingService.presetSubtitle(presetIndex, utilityPresetCount);
    }

    @Override
    public boolean isRelicInHand(@Nonnull Ref<EntityStore> ref,
                                 @Nonnull Store<EntityStore> store,
                                 @Nonnull UUID uuid) {
        ItemStack stack = InventoryComponent.getItemInHand(store, ref);
        return stack != null && RELIC_ITEM_ID.equals(stack.getItemId());
    }

    @Nullable
    private RelicAbilityView toAbilityView(@Nullable Skill skill) {
        if (skill == null || skill.abilityId == null || skill.abilityId.isBlank()) {
            return null;
        }
        com.epicseed.epiccore.skill.model.Ability ability = DEFINITIONS.getAbility(skill.abilityId);
        String displayName = ability != null && ability.displayName != null && !ability.displayName.isBlank()
                ? ability.displayName
                : skill.displayName;
        String description = ability != null && ability.description != null && !ability.description.isBlank()
                ? ability.description
                : null;
        return new RelicAbilityView(skill.abilityId, displayName, description, skill.rarity, skill.iconPath);
    }

}
