package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.hytale.TeleportAdapter;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeDefinitions;
import com.epicseed.vampirism.util.WorldPositionHelper;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public final class TeleportActionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final double DEFAULT_TELEPORT_RANGE = 8.0;
    private static final double TELEPORT_LOOK_START_OFFSET = 0.5;
    private static final double TELEPORT_MIN_DISTANCE = 1.5;
    private static final double TELEPORT_WALL_BUFFER = 0.3;

    private TeleportActionHandler() {
    }

    public static boolean teleport(Map<String, Object> action, SkillRuntimeContext ctx) {
        TransformComponent transform = (TransformComponent) ctx.store().getComponent(
                ctx.ref(), TransformComponent.getComponentType());
        if (transform == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] teleport: caster has no TransformComponent");
            return false;
        }

        World world = TeleportAdapter.resolveWorld(ctx.store());
        if (world == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] teleport: caster world unavailable");
            return false;
        }

        Vector3d target = resolveTarget(action, ctx, world);
        if (target == null) {
            return false;
        }

        Vector3d safeTarget = WorldPositionHelper.findSafeGroundPosition(world, target);
        if (safeTarget != null) {
            target = safeTarget;
        }

        try {
            TeleportAdapter.teleportPlayer(ctx.ref(), ctx.store(), world, target, transform);
            LOGGER.atInfo().log(String.format("[SkillActionExecutor] teleport: moved to %.1f,%.1f,%.1f",
                    target.x, target.y, target.z));
            return true;
        } catch (Exception ex) {
            LOGGER.atWarning().log("[SkillActionExecutor] teleport: native teleport failed - " + ex.getMessage());
            return false;
        }
    }

    @Nullable
    private static Vector3d resolveTarget(Map<String, Object> action,
                                          SkillRuntimeContext ctx,
                                          World world) {
        String mode = action.get("mode") instanceof String s ? s : "forward";
        if ("toTarget".equals(mode)) {
            Ref<EntityStore> targetRef = ctx.targetRef();
            if (targetRef == null || targetRef.equals(ctx.ref())) {
                LOGGER.atWarning().log("[SkillActionExecutor] teleport toTarget: no targetRef");
                return null;
            }
            TransformComponent targetTransform = (TransformComponent) ctx.store().getComponent(
                    targetRef, TransformComponent.getComponentType());
            return targetTransform != null ? targetTransform.getPosition() : null;
        }

        Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
        if (look == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] teleport: caster look transform unavailable");
            return null;
        }

        Vector3d target = resolveLookTeleportTarget(world, look, resolveTeleportDistance(action));
        if (target == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] teleport: unable to resolve a safe look target");
        }
        return target;
    }

    private static double resolveTeleportDistance(Map<String, Object> action) {
        if (action.get("distance") instanceof Number d) {
            return d.doubleValue() > 0d ? d.doubleValue() : DEFAULT_TELEPORT_RANGE;
        }
        Object targetingIdObj = action.get("targetingId");
        if (targetingIdObj instanceof String targetingId && !targetingId.isBlank()) {
            Map<String, Object> targetingSpec = SkillRuntimeDefinitions.resolveTargeting(Map.of("targetingId", targetingId));
            if (targetingSpec.get("maxRange") instanceof Number r && r.doubleValue() > 0d) {
                return r.doubleValue();
            }
        }
        return DEFAULT_TELEPORT_RANGE;
    }

    @Nullable
    private static Vector3d resolveLookTeleportTarget(@Nonnull World world,
                                                      @Nonnull Transform look,
                                                      double teleportRange) {
        Vector3d position = look.getPosition();
        Vector3d direction = look.getDirection();
        if (direction == null || direction.length() < 0.001d) {
            return null;
        }

        Vector3d directionNormalized = direction.clone().normalize();
        Vector3d raycastStart = directionNormalized.clone().scale(TELEPORT_LOOK_START_OFFSET).add(position);
        Vector3i hitBlock = TargetUtil.getTargetBlock(world, (blockId, fluidId) -> isSolidBlockId(blockId),
                raycastStart.x, raycastStart.y, raycastStart.z,
                directionNormalized.x, directionNormalized.y, directionNormalized.z,
                teleportRange);

        if (hitBlock == null) {
            return directionNormalized.clone().scale(teleportRange).add(position);
        }

        Vector3d blockMin = new Vector3d(hitBlock.x, hitBlock.y, hitBlock.z);
        Vector3d blockMax = new Vector3d(hitBlock.x + 1.0, hitBlock.y + 1.0, hitBlock.z + 1.0);
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    Vector3d corner = new Vector3d(
                            i == 0 ? blockMin.x : blockMax.x,
                            j == 0 ? blockMin.y : blockMax.y,
                            k == 0 ? blockMin.z : blockMax.z);
                    double dist = corner.clone().subtract(position).dot(directionNormalized);
                    if (dist > 0.0d && dist < minDist) {
                        minDist = dist;
                    }
                }
            }
        }

        if (minDist < Double.MAX_VALUE && minDist >= TELEPORT_MIN_DISTANCE) {
            return directionNormalized.clone().scale(minDist - TELEPORT_WALL_BUFFER).add(position);
        }
        if (minDist < Double.MAX_VALUE) {
            return position;
        }
        return directionNormalized.clone().scale(teleportRange).add(position);
    }

    private static boolean isSolidBlockId(int blockId) {
        if (blockId == 0) {
            return false;
        }
        BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
        return blockType != null && blockType.getMaterial() == BlockMaterial.Solid;
    }
}
