package com.epicseed.vampirism.runtime;

import java.util.UUID;
import java.util.Objects;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker;

public final class ProgressionLifecycleService {

    private static PlayerSkillRegistry playerSkillRegistry;

    private ProgressionLifecycleService() {
    }

    public static void init(@Nonnull PlayerSkillRegistry playerSkillRegistry) {
        ProgressionLifecycleService.playerSkillRegistry = Objects.requireNonNull(playerSkillRegistry, "playerSkillRegistry");
    }

    public static void onPlayerConnect(@Nonnull UUID uuid) {
        PlayerSkillRegistry registry = registry();
        registry.onPlayerConnect(uuid);
        AbilityCooldownTracker.restorePlayer(uuid, registry.getPersistedAbilityCooldowns(uuid));
    }

    public static void captureDisconnectProgress(@Nonnull UUID uuid) {
        PlayerSkillRegistry registry = registry();
        registry.setPersistedAbilityCooldowns(uuid, AbilityCooldownTracker.snapshotRemaining(uuid));
        registry.onPlayerDisconnect(uuid);
    }

    @Nonnull
    private static PlayerSkillRegistry registry() {
        if (playerSkillRegistry == null) throw new IllegalStateException("ProgressionLifecycleService not initialized!");
        return playerSkillRegistry;
    }
}
