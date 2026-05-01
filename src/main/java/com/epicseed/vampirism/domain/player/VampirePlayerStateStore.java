package com.epicseed.vampirism.domain.player;

import com.epicseed.epiccore.player.PlayerProgressStore;

import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Stores and retrieves Vampirism-specific persisted player state:
 * blood, infection timing, and night hunt progress/cooldown.
 *
 * <p>Backed by the same {@link PlayerProgressStore} that drives EpicCore
 * progression, so all data lives in a single per-player JSON file. Lifecycle
 * (connect/disconnect/save) is managed by
 * {@link com.epicseed.vampirism.skill.registry.PlayerSkillRegistry}.</p>
 */
public final class VampirePlayerStateStore {

    private static VampirePlayerStateStore instance;

    private final PlayerProgressStore<PlayerVampireProfile> store;

    private VampirePlayerStateStore(@Nonnull PlayerProgressStore<PlayerVampireProfile> store) {
        this.store = store;
    }

    /**
     * Called once from
     * {@link com.epicseed.vampirism.skill.registry.PlayerSkillRegistry#init(java.nio.file.Path)}.
     * Shares the already-constructed store so both registries operate on the same cache.
     */
    public static void init(@Nonnull PlayerProgressStore<PlayerVampireProfile> store) {
        instance = new VampirePlayerStateStore(store);
    }

    @Nonnull
    public static VampirePlayerStateStore get() {
        if (instance == null) {
            throw new IllegalStateException("VampirePlayerStateStore not initialized!");
        }
        return instance;
    }

    // ── Blood ─────────────────────────────────────────────────────────────────

    public int getPersistedBlood(@Nonnull UUID uuid) {
        return store.readProfile(uuid, profile -> profile.blood);
    }

    public void setPersistedBlood(@Nonnull UUID uuid, int blood) {
        store.mutateProfile(uuid, profile -> profile.blood = Math.max(0, blood));
    }

    // ── Infection ─────────────────────────────────────────────────────────────

    public long getInfectionExpiresAtMs(@Nonnull UUID uuid) {
        return store.readProfile(uuid, profile -> Math.max(0L, profile.infectionExpiresAtMs));
    }

    public long getInfectionRemainingMs(@Nonnull UUID uuid) {
        return Math.max(0L, getInfectionExpiresAtMs(uuid) - System.currentTimeMillis());
    }

    public boolean isInfected(@Nonnull UUID uuid) {
        return getInfectionRemainingMs(uuid) > 0L;
    }

    public void setInfectionExpiresAtMs(@Nonnull UUID uuid, long expiresAtMs) {
        store.mutateAndSaveProfile(uuid,
                profile -> profile.infectionExpiresAtMs = Math.max(0L, expiresAtMs));
    }

    public void clearInfection(@Nonnull UUID uuid) {
        setInfectionExpiresAtMs(uuid, 0L);
    }

    // ── Night Hunt ────────────────────────────────────────────────────────────

    public int getCompletedNightHunts(@Nonnull UUID uuid) {
        return store.readProfile(uuid, profile -> profile.completedNightHunts);
    }

    public void incrementCompletedNightHunts(@Nonnull UUID uuid) {
        store.mutateAndSaveProfile(uuid,
                profile -> profile.completedNightHunts = Math.max(0, profile.completedNightHunts + 1));
    }

    public long getPersistedNightHuntCooldownMs(@Nonnull UUID uuid) {
        return store.readProfile(uuid, profile -> profile.nightHuntCooldownMs);
    }

    public void setPersistedNightHuntCooldownMs(@Nonnull UUID uuid, long cooldownMs) {
        store.mutateProfile(uuid,
                profile -> profile.nightHuntCooldownMs = Math.max(0L, cooldownMs));
    }

    // ── Generic escape hatch ──────────────────────────────────────────────────

    /**
     * Applies a raw mutation to the player's full profile and immediately persists.
     * Prefer typed accessors above; use this only when multiple fields must change atomically.
     */
    public void updateProfile(@Nonnull UUID uuid,
                              @Nonnull Consumer<PlayerVampireProfile> updater) {
        store.mutateAndSaveProfile(uuid, updater);
    }
}
