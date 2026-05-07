package com.epicseed.vampirism.domain.ritual;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.epiccore.vampirism.skill.runtime.VampirismSkillProgressionAccess;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualContextResolver {

    private final VampirismSkillProgressionAccess progressionAccess;

    public VampiricRitualContextResolver(@Nonnull VampirismSkillProgressionAccess progressionAccess) {
        this.progressionAccess = progressionAccess;
    }

    @Nonnull
    public VampiricRitualContext buildContext(@Nonnull PlayerRef target,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Set<String> extraTags) {
        return buildContext(target.getUuid(), store, extraTags);
    }

    @Nonnull
    public VampiricRitualContext buildContext(@Nonnull UUID uuid,
                                              @Nullable Store<EntityStore> store,
                                              @Nonnull Set<String> extraTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (VampirePlayerStateStore.get().isInfected(uuid)) {
            tags.add(VampiricRitualRegistry.TAG_INFECTED);
        }
        if (isNight(store)) {
            tags.add(VampiricRitualRegistry.TAG_NIGHT);
        }
        tags.addAll(extraTags);
        int blood = VampireVitalitySystem.getBloodByUuid(uuid);
        if (blood < 0) {
            blood = VampirePlayerStateStore.get().getPersistedBlood(uuid);
        }
        return new VampiricRitualContext(
                uuid,
                blood,
                VampirePlayerStateStore.get().getCompletedNightHunts(uuid),
                VampirePlayerStateStore.get().getAgeTierId(uuid),
                progressionAccess.getUnlockedSkillIds(uuid),
                Set.copyOf(tags));
    }

    public boolean isNight(@Nullable Store<EntityStore> store) {
        if (store == null) {
            return false;
        }
        WorldTimeResource worldTime = store.getResource(WorldTimeResource.getResourceType());
        if (worldTime == null) {
            return false;
        }
        int currentHour = worldTime.getCurrentHour();
        int nightStart = VampirismConfig.get().getNightStartHour();
        int dayStart = VampirismConfig.get().getDayStartHour();
        return currentHour >= nightStart || currentHour < dayStart;
    }
}
