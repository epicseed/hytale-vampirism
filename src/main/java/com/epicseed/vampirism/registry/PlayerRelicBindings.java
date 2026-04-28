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
        return PlayerSkillRegistry.get().getRelicBindings(uuid);
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
        String v = PlayerSkillRegistry.get().getRelicBinding(uuid, slot);
        if (v != null) return v.isBlank() ? null : v;
        return RelicBindings.abilityFor(slot);
    }

    public void set(@Nonnull UUID uuid, @Nonnull String slot, @Nonnull String abilityId) {
        PlayerSkillRegistry.get().setRelicBinding(uuid, slot, abilityId);
    }

    public void clear(@Nonnull UUID uuid, @Nonnull String slot) {
        PlayerSkillRegistry.get().clearRelicBinding(uuid, slot);
    }

    public void resetAll(@Nonnull UUID uuid) {
        PlayerSkillRegistry.get().resetRelicBindings(uuid);
    }

    /** Persists the current in-memory state to disk. */
    public void save() {
        LOGGER.atFinest().log("[PlayerRelicBindings] Save handled by PlayerSkillRegistry.");
    }
}
