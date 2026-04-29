package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.config.VampirismConfig;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampireThroneRecoveryService {
    private static final String VAMPIRE_THRONE_BLOCK_ID = "VampireThrone";
    private static final float VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS = 2.5f;

    private VampireThroneRecoveryService() {
    }

    public static void apply(@Nonnull Ref<EntityStore> playerRef,
                             @Nonnull Player player,
                             @Nonnull Store<EntityStore> store,
                             @Nonnull BloodState state,
                             float elapsedSeconds) {
        if (!isMountedOnVampireThrone(playerRef, player, store)) {
            state.vampireThroneRecoveryAccumulator = 0f;
            return;
        }
        if (state.blood >= state.maxBlood) {
            state.vampireThroneRecoveryAccumulator = 0f;
            return;
        }

        state.vampireThroneRecoveryAccumulator += elapsedSeconds;
        if (state.vampireThroneRecoveryAccumulator < VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS) {
            return;
        }

        int recoveryTicks = (int) (state.vampireThroneRecoveryAccumulator / VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS);
        state.vampireThroneRecoveryAccumulator -= recoveryTicks * VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS;
        BloodState updated = BloodService.addBlood(
                playerRef,
                recoveryTicks * VampirismConfig.get().getVampireThroneRecoveryBlood());
        BloodHudService.syncBlood(playerRef, updated);
    }

    private static boolean isMountedOnVampireThrone(@Nonnull Ref<EntityStore> playerRef,
                                                    @Nonnull Player player,
                                                    @Nonnull Store<EntityStore> store) {
        MountedComponent mounted = store.getComponent(playerRef, MountedComponent.getComponentType());
        if (mounted == null || mounted.getMountedToBlock() == null || !mounted.getMountedToBlock().isValid()) {
            return false;
        }

        World world = player.getWorld();
        if (world == null) {
            return false;
        }

        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null || chunkStore.getStore() == null) {
            return false;
        }

        BlockMountComponent blockMount;
        try {
            blockMount = chunkStore.getStore().getComponent(
                    mounted.getMountedToBlock(),
                    BlockMountComponent.getComponentType());
        } catch (IllegalStateException ignored) {
            return false;
        }
        if (blockMount == null || blockMount.getExpectedBlockType() == null) {
            return false;
        }

        return VAMPIRE_THRONE_BLOCK_ID.equals(blockMount.getExpectedBlockType().getId());
    }
}
