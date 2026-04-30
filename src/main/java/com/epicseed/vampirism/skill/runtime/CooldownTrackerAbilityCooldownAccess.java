package com.epicseed.vampirism.skill.runtime;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.skill.progression.AbilityCooldownAccess;

public final class CooldownTrackerAbilityCooldownAccess implements AbilityCooldownAccess {

    private static final CooldownTrackerAbilityCooldownAccess INSTANCE = new CooldownTrackerAbilityCooldownAccess();

    private CooldownTrackerAbilityCooldownAccess() {
    }

    public static CooldownTrackerAbilityCooldownAccess instance() {
        return INSTANCE;
    }

    @Override
    public long remainingMs(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return AbilityCooldownTracker.getRemainingMs(uuid, abilityId);
    }
}
