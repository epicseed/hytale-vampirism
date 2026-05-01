package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.player.VampirePlayerStateStore;
import com.epicseed.vampirism.skill.runtime.PlayerRegistrySkillProgressionAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntRewardService {
    private NightHuntRewardService() {
    }

    public static void grantCompletionReward(@Nonnull UUID ownerUuid,
                                             @Nullable Ref<EntityStore> rewardRef,
                                             @Nonnull Store<EntityStore> store,
                                             int rewardPoints) {
        VampirePlayerStateStore.get().incrementCompletedNightHunts(ownerUuid);
        if (rewardPoints <= 0) {
            return;
        }
        PlayerRegistrySkillProgressionAccess.instance().addSkillPoints(ownerUuid, rewardPoints);
        if (rewardRef != null && rewardRef.isValid()) {
            NightHuntMessages.send(rewardRef, store, NightHuntMessages.rewardText(rewardPoints), "green");
        }
    }
}
