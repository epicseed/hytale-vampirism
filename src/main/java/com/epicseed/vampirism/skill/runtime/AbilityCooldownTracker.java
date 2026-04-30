package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.UUID;

public final class AbilityCooldownTracker {

    private AbilityCooldownTracker() {
    }

    public static boolean tryUse(@Nonnull UUID uuid, @Nonnull String abilityId, long cooldownMs) {
        return com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker.tryUse(uuid, abilityId, cooldownMs);
    }

    public static long getRemainingMs(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker.getRemainingMs(uuid, abilityId);
    }

    public static boolean isOnCooldown(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker.isOnCooldown(uuid, abilityId);
    }

    @Nonnull
    public static Map<String, Long> snapshotRemaining(@Nonnull UUID uuid) {
        return com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker.snapshotRemaining(uuid);
    }

    public static void restorePlayer(@Nonnull UUID uuid, @Nonnull Map<String, Long> snapshot) {
        com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker.restorePlayer(uuid, snapshot);
    }

    public static void reset(@Nonnull UUID uuid, @Nonnull String abilityId) {
        com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker.reset(uuid, abilityId);
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker.clearPlayer(uuid);
    }
}
