package com.epicseed.vampirism.util;

import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WorldPositionHelper {

    private WorldPositionHelper() {
    }

    @Nullable
    public static Vector3d findSafeGroundPosition(@Nonnull World world, @Nonnull Vector3d targetPos) {
        return findSafeGroundPosition(world.getNonTickingChunk(chunkKeyFor(targetPos)), targetPos);
    }

    /**
     * Variant for hot in-tick systems: only inspects already loaded chunks and never triggers chunk loading.
     */
    @Nullable
    public static Vector3d findSafeGroundPositionIfLoaded(@Nonnull World world, @Nonnull Vector3d targetPos) {
        return findSafeGroundPosition(world.getChunkIfLoaded(chunkKeyFor(targetPos)), targetPos);
    }

    private static long chunkKeyFor(@Nonnull Vector3d targetPos) {
        int blockX = (int) Math.floor(targetPos.x);
        int blockZ = (int) Math.floor(targetPos.z);
        return ((long) (blockX >> 5) << 32) | ((long) (blockZ >> 5) & 0xFFFFFFFFL);
    }

    @Nullable
    private static Vector3d findSafeGroundPosition(@Nullable WorldChunk chunk, @Nonnull Vector3d targetPos) {
        if (chunk == null) {
            return null;
        }

        int blockX = (int) Math.floor(targetPos.x);
        int blockY = (int) Math.floor(targetPos.y);
        int blockZ = (int) Math.floor(targetPos.z);

        int scanUp = 0;
        while (scanUp < 10 && blockY + scanUp < 320 && isSolidBlock(chunk, blockX, blockY + scanUp, blockZ)) {
            scanUp++;
        }

        int groundY = -1;
        int startY = blockY + scanUp;
        for (int dy = 0; dy < 20 && startY - dy >= 0; dy++) {
            int checkY = startY - dy;
            if (isSolidBlock(chunk, blockX, checkY, blockZ)) {
                groundY = checkY;
                break;
            }
        }
        if (groundY >= 0) {
            return new Vector3d(targetPos.x, groundY + 1.1d, targetPos.z);
        }

        for (int dy = 0; dy < 10 && blockY + scanUp + dy < 320; dy++) {
            int checkY = blockY + scanUp + dy;
            if (isSolidBlock(chunk, blockX, checkY, blockZ)) {
                continue;
            }
            if (checkY <= 0 || !isSolidBlock(chunk, blockX, checkY - 1, blockZ)) {
                continue;
            }
            return new Vector3d(targetPos.x, checkY + 0.1d, targetPos.z);
        }

        return new Vector3d(targetPos.x, Math.max(targetPos.y + 0.5d, blockY + 1.1d), targetPos.z);
    }

    private static boolean isSolidBlock(@Nonnull WorldChunk chunk, int x, int y, int z) {
        BlockType blockType = chunk.getBlockType(x, y, z);
        return blockType != null && blockType.getMaterial() == BlockMaterial.Solid;
    }
}
