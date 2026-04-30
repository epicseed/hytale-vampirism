package com.epicseed.vampirism.registry;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.RelicBindings;
import com.hypixel.hytale.logger.HytaleLogger;

/**
 * Per-player override of the global {@link RelicBindings} relic-slot map.
 *
 * <p>Bindings are persisted inside each player's
 * {@code PlayerSkills/{uuid}.json} via {@link PlayerSkillRegistry}.
 */
public class PlayerRelicBindings {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static PlayerRelicBindings instance;

    private PlayerRelicBindings() {}

    public static void init(@Nonnull Path dataDirectory) {
        instance = new PlayerRelicBindings();
        LOGGER.atInfo().log("[PlayerRelicBindings] Using PlayerSkills/{uuid}.json for per-player relic bindings.");
    }

    @Nonnull
    public static PlayerRelicBindings get() {
        if (instance == null) throw new IllegalStateException("PlayerRelicBindings not initialized!");
        return instance;
    }

    /** Returns a defensive copy of the player's overrides (never null). */
    @Nonnull
    public Map<String, String> bindingsFor(@Nonnull UUID uuid) {
        return bindingsFor(uuid, activePresetIndex(uuid));
    }

    @Nonnull
    public Map<String, String> bindingsFor(@Nonnull UUID uuid, int presetIndex) {
        return PlayerSkillRegistry.get().getRelicBindings(uuid, presetIndex);
    }

    public int activePresetIndex(@Nonnull UUID uuid) {
        return PlayerSkillRegistry.get().getActiveRelicPresetIndex(uuid);
    }

    public void setActivePreset(@Nonnull UUID uuid, int presetIndex) {
        PlayerSkillRegistry.get().setActiveRelicPresetIndex(uuid, presetIndex);
    }

    public void setAll(@Nonnull UUID uuid,
                       @Nonnull Map<Integer, ? extends Map<String, String>> presetBindings,
                       int activePresetIndex) {
        PlayerSkillRegistry.get().setRelicBindings(uuid, presetBindings, activePresetIndex);
    }

    /**
     * Resolves the effective binding for {@code slot}: per-player override first,
     * then the global {@link RelicBindings}, then null.
     *
     * <p>An override of {@code ""} means the slot is intentionally empty — it returns
     * {@code null} without falling back to the global default.
     */
    @Nullable
    public String abilityFor(@Nonnull UUID uuid, @Nonnull String slot) {
        return abilityFor(uuid, activePresetIndex(uuid), slot);
    }

    @Nullable
    public String abilityFor(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot) {
        String v = PlayerSkillRegistry.get().getRelicBinding(uuid, presetIndex, slot);
        if (v != null) return v.isBlank() ? null : v;
        return RelicBindings.abilityFor(slot);
    }

    public void set(@Nonnull UUID uuid, @Nonnull String slot, @Nonnull String abilityId) {
        set(uuid, activePresetIndex(uuid), slot, abilityId);
    }

    public void set(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot, @Nonnull String abilityId) {
        PlayerSkillRegistry.get().setRelicBinding(uuid, presetIndex, slot, abilityId);
    }

    public void setAll(@Nonnull UUID uuid, @Nonnull Map<String, String> bindings) {
        setAll(uuid, activePresetIndex(uuid), bindings);
    }

    public void setAll(@Nonnull UUID uuid, int presetIndex, @Nonnull Map<String, String> bindings) {
        PlayerSkillRegistry.get().setRelicBindings(uuid, presetIndex, bindings);
    }

    public void clear(@Nonnull UUID uuid, @Nonnull String slot) {
        clear(uuid, activePresetIndex(uuid), slot);
    }

    public void clear(@Nonnull UUID uuid, int presetIndex, @Nonnull String slot) {
        PlayerSkillRegistry.get().clearRelicBinding(uuid, presetIndex, slot);
    }

    public void resetAll(@Nonnull UUID uuid) {
        resetAll(uuid, activePresetIndex(uuid));
    }

    public void resetAll(@Nonnull UUID uuid, int presetIndex) {
        PlayerSkillRegistry.get().resetRelicBindings(uuid, presetIndex);
    }

    /** Persists the current in-memory state to disk. */
    public void save() {
        LOGGER.atFinest().log("[PlayerRelicBindings] Save handled by PlayerSkillRegistry.");
    }
}
