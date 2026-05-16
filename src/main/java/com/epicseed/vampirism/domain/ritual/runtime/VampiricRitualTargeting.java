package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public final class VampiricRitualTargeting {

    public static final String RITUAL_TOOL_ITEM_ID = "VampirismRitualTool";
    public static final double TARGET_RANGE = 7.0d;
    public static final double MAX_CHANNEL_DISTANCE = 6.0d;
    private static final int ANCHOR_SEARCH_RADIUS = 4;

    private VampiricRitualTargeting() {
    }

    @Nullable
    public static TargetedBlock resolveTargetedBlock(@Nonnull Ref<EntityStore> ref,
                                                     @Nonnull Store<EntityStore> store,
                                                     @Nonnull World world) {
        return resolveTargetedBlock(ref, store, world, true);
    }

    /**
     * Hot in-tick variant that never triggers chunk loading while sampling the hit block.
     */
    @Nullable
    public static TargetedBlock resolveTargetedBlockIfLoaded(@Nonnull Ref<EntityStore> ref,
                                                             @Nonnull Store<EntityStore> store,
                                                             @Nonnull World world) {
        return resolveTargetedBlock(ref, store, world, false);
    }

    @Nullable
    private static TargetedBlock resolveTargetedBlock(@Nonnull Ref<EntityStore> ref,
                                                      @Nonnull Store<EntityStore> store,
                                                      @Nonnull World world,
                                                      boolean allowChunkLoading) {
        Transform look = TargetUtil.getLook(ref, store);
        if (look == null || look.getDirection() == null || look.getDirection().length() < 0.001d) {
            return null;
        }

        Vector3d direction = new Vector3d(look.getDirection()).normalize();
        Vector3d raycastStart = new Vector3d(look.getPosition()).addScaled(direction, 0.35d);
        Vector3i hitBlock = TargetUtil.getTargetBlock(
                world,
                (blockId, fluidId) -> isSolidBlockId(blockId),
                raycastStart.x, raycastStart.y, raycastStart.z,
                direction.x, direction.y, direction.z,
                TARGET_RANGE);
        if (hitBlock == null) {
            return null;
        }

        BlockType blockType = blockTypeAt(world, hitBlock, allowChunkLoading);
        if (blockType == null) {
            return null;
        }
        String blockId = blockType.getId();
        return new TargetedBlock(
                new Vector3i(hitBlock.x, hitBlock.y, hitBlock.z),
                new Vector3d(hitBlock.x + 0.5d, hitBlock.y + 0.15d, hitBlock.z + 0.5d),
                blockId);
    }

    public static boolean isRitualAnchor(@Nullable TargetedBlock targetedBlock,
                                         @Nonnull Predicate<String> supportedAnchorBlockIds) {
        Objects.requireNonNull(supportedAnchorBlockIds, "supportedAnchorBlockIds");
        return targetedBlock != null && supportedAnchorBlockIds.test(targetedBlock.blockId());
    }

    @Nullable
    public static TargetedBlock resolveRitualAnchorNear(@Nonnull World world,
                                                        @Nonnull Vector3i centerBlockPosition,
                                                        @Nonnull Predicate<String> supportedAnchorBlockIds) {
        Objects.requireNonNull(supportedAnchorBlockIds, "supportedAnchorBlockIds");
        TargetedBlock nearest = null;
        double nearestDistanceSq = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            int y = centerBlockPosition.y + dy;
            for (int dx = -ANCHOR_SEARCH_RADIUS; dx <= ANCHOR_SEARCH_RADIUS; dx++) {
                for (int dz = -ANCHOR_SEARCH_RADIUS; dz <= ANCHOR_SEARCH_RADIUS; dz++) {
                    Vector3i candidate = new Vector3i(centerBlockPosition.x + dx, y, centerBlockPosition.z + dz);
                    BlockType blockType = blockTypeAt(world, candidate, true);
                    if (blockType == null || !supportedAnchorBlockIds.test(blockType.getId())) {
                        continue;
                    }
                    double distanceSq = dx * dx + dy * dy + dz * dz;
                    if (distanceSq < nearestDistanceSq) {
                        nearestDistanceSq = distanceSq;
                        nearest = new TargetedBlock(
                                candidate,
                                new Vector3d(candidate.x + 0.5d, candidate.y + 0.15d, candidate.z + 0.5d),
                                blockType.getId());
                    }
                }
            }
        }
        return nearest;
    }

    @Nullable
    public static Vector3d resolvePointTarget(@Nonnull Ref<EntityStore> ref,
                                              @Nonnull Store<EntityStore> store,
                                              @Nonnull Vector3d ritualPlaneCenter,
                                              @Nullable TargetedBlock fallbackTarget) {
        Transform look = TargetUtil.getLook(ref, store);
        if (look != null && look.getDirection() != null && look.getDirection().length() >= 0.001d) {
            Vector3d origin = new Vector3d(look.getPosition());
            Vector3d direction = new Vector3d(look.getDirection());
            if (Math.abs(direction.y) >= 0.001d) {
                double t = (ritualPlaneCenter.y - origin.y) / direction.y;
                if (Double.isFinite(t) && t > 0d && t <= TARGET_RANGE) {
                    return new Vector3d(
                            origin.x + direction.x * t,
                            ritualPlaneCenter.y,
                            origin.z + direction.z * t);
                }
            }
        }
        return fallbackTarget != null
                ? new Vector3d(fallbackTarget.topCenter().x, ritualPlaneCenter.y, fallbackTarget.topCenter().z)
                : null;
    }

    public static boolean isAnchorBlock(@Nonnull World world, @Nonnull Vector3i blockPosition, @Nonnull String blockId) {
        BlockType type = blockTypeAt(world, blockPosition, true);
        return type != null && blockId.equals(type.getId());
    }

    /**
     * Returns null when the anchor chunk is not loaded yet.
     */
    @Nullable
    public static Boolean isAnchorBlockIfLoaded(@Nonnull World world,
                                                @Nonnull Vector3i blockPosition,
                                                @Nonnull String blockId) {
        long chunkKey = chunkKey(blockPosition);
        WorldChunk chunk = world.getChunkIfLoaded(chunkKey);
        if (chunk == null) {
            return null;
        }
        BlockType type = chunk.getBlockType(blockPosition.x, blockPosition.y, blockPosition.z);
        return type != null && blockId.equals(type.getId());
    }

    public static boolean isNearAnchor(@Nonnull Ref<EntityStore> ref,
                                       @Nonnull Store<EntityStore> store,
                                       @Nonnull Vector3d anchorCenter,
                                       double maxDistance) {
        return distanceToAnchor(ref, store, anchorCenter) <= maxDistance;
    }

    public static double distanceToAnchor(@Nonnull Ref<EntityStore> ref,
                                          @Nonnull Store<EntityStore> store,
                                          @Nonnull Vector3d anchorCenter) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return Double.POSITIVE_INFINITY;
        }
        Vector3d position = new Vector3d(transform.getPosition());
        double dx = position.x - anchorCenter.x;
        double dy = position.y - anchorCenter.y;
        double dz = position.z - anchorCenter.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    @Nullable
    private static BlockType blockTypeAt(@Nonnull World world, @Nonnull Vector3i blockPosition) {
        return blockTypeAt(world, blockPosition, true);
    }

    @Nullable
    private static BlockType blockTypeAt(@Nonnull World world,
                                         @Nonnull Vector3i blockPosition,
                                         boolean allowChunkLoading) {
        long chunkKey = chunkKey(blockPosition);
        WorldChunk chunk = allowChunkLoading ? world.getNonTickingChunk(chunkKey) : world.getChunkIfLoaded(chunkKey);
        return chunk != null ? chunk.getBlockType(blockPosition.x, blockPosition.y, blockPosition.z) : null;
    }

    private static long chunkKey(@Nonnull Vector3i blockPosition) {
        return (((long) (blockPosition.x >> 5)) << 32) | (((long) (blockPosition.z >> 5)) & 0xFFFFFFFFL);
    }

    private static boolean isSolidBlockId(int blockId) {
        if (blockId == 0) {
            return false;
        }
        BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
        return blockType != null;
    }

    public record TargetedBlock(@Nonnull Vector3i blockPosition,
                                @Nonnull Vector3d topCenter,
                                @Nonnull String blockId) {
    }
}
