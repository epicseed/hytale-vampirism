package com.epicseed.vampirism.runtime;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.epiccore.skill.runtime.AbilityCooldownTracker;

public final class ProgressionLifecycleService {

    private ProgressionLifecycleService() {
    }

    public static void onPlayerConnect(@Nonnull UUID uuid) {
        PlayerSkillRegistry.get().onPlayerConnect(uuid);
        AbilityCooldownTracker.restorePlayer(uuid, PlayerSkillRegistry.get().getPersistedAbilityCooldowns(uuid));
    }

    public static void captureDisconnectProgress(@Nonnull UUID uuid) {
        PlayerSkillRegistry.get().setPersistedAbilityCooldowns(uuid, AbilityCooldownTracker.snapshotRemaining(uuid));
        PlayerSkillRegistry.get().onPlayerDisconnect(uuid);
    }
}
