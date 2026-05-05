package com.epicseed.vampirism.domain.ritual;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nonnull;

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
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (VampirePlayerStateStore.get().isInfected(target.getUuid())) {
            tags.add(VampiricRitualRegistry.TAG_INFECTED);
        }
        if (isNight(store)) {
            tags.add(VampiricRitualRegistry.TAG_NIGHT);
        }
        tags.addAll(extraTags);
        int blood = VampireVitalitySystem.getBloodByUuid(target.getUuid());
        if (blood < 0) {
            blood = VampirePlayerStateStore.get().getPersistedBlood(target.getUuid());
        }
        return new VampiricRitualContext(
                target.getUuid(),
                blood,
                VampirePlayerStateStore.get().getCompletedNightHunts(target.getUuid()),
                VampirePlayerStateStore.get().getAgeTierId(target.getUuid()),
                progressionAccess.getUnlockedSkillIds(target.getUuid()),
                Set.copyOf(tags));
    }

    public boolean isNight(@Nonnull Store<EntityStore> store) {
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
