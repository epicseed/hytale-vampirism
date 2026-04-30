package com.epicseed.epiccore.skill.progression;

import java.util.UUID;

public interface AbilityCooldownAccess {

    long remainingMs(UUID uuid, String abilityId);
}
