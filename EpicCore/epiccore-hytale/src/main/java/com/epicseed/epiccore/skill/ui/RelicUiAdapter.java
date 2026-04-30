package com.epicseed.epiccore.skill.ui;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public interface RelicUiAdapter {

    int maxPresetCount();

    long cooldownRefreshIntervalMs();

    int totalPresetCount(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store);

    int activePresetIndex(@Nonnull UUID uuid);

    void setActivePreset(@Nonnull UUID uuid, int presetIndex);

    int clampPresetIndex(int presetIndex, int presetCount);

    @Nonnull
    Map<String, String> getEffectiveBindings(@Nonnull UUID uuid, int presetIndex);

    void applyAllBindings(@Nonnull UUID uuid,
                          @Nonnull Map<Integer, ? extends Map<String, String>> pendingByPreset,
                          int activePresetIndex);

    @Nonnull
    List<RelicAbilityView> listBindableAbilities(@Nonnull UUID uuid);

    @Nullable
    RelicAbilityView describeAbility(@Nullable String abilityId);

    @Nullable
    String defaultAbilityId(@Nonnull String slot);

    @Nullable
    String activeAbilityForSlot(@Nonnull UUID uuid, @Nonnull String slot);

    long remainingMs(@Nonnull UUID uuid, @Nonnull String abilityId);

    long slotBindingRemainingMs(@Nonnull UUID uuid,
                                @Nonnull Map<String, String> savedState,
                                @Nonnull String slot);

    boolean isAbilityOnCooldown(@Nonnull UUID uuid, @Nullable String abilityId);

    @Nullable
    String validatePendingBindings(@Nonnull UUID uuid,
                                   @Nonnull Map<String, String> savedState,
                                   @Nonnull Map<String, String> pending);

    @Nonnull
    String abilityCooldownMessage(@Nonnull UUID uuid, @Nonnull String abilityId);

    @Nonnull
    String slotLabel(@Nonnull String slot);

    @Nonnull
    String slotHint(@Nonnull String slot);

    @Nonnull
    String presetLabel(int presetIndex, int utilityPresetCount);

    @Nonnull
    String presetSubtitle(int presetIndex, int utilityPresetCount);

    boolean isRelicInHand(@Nonnull Ref<EntityStore> ref,
                          @Nonnull Store<EntityStore> store,
                          @Nonnull UUID uuid);
}
