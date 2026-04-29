package com.epicseed.vampirism.domain.blood;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.component.NPCMarkerComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class FeedEligibility {
    private FeedEligibility() {
    }

    public static boolean isAlive(@Nonnull Ref<EntityStore> targetRef, @Nonnull Store<EntityStore> store) {
        EntityStatValue health = resolveHealth(targetRef, store);
        return health != null && health.get() > 0f;
    }

    public static boolean isAtOrBelowHealthPercent(@Nonnull Ref<EntityStore> targetRef,
                                                   float threshold,
                                                   @Nonnull Store<EntityStore> store) {
        EntityStatValue health = resolveHealth(targetRef, store);
        if (health == null || health.get() <= 0f || health.getMax() <= 0f) {
            return false;
        }
        return health.get() / health.getMax() <= Math.max(0f, threshold);
    }

    public static boolean isWithinRange(@Nonnull Ref<EntityStore> sourceRef,
                                        @Nonnull Ref<EntityStore> targetRef,
                                        @Nonnull Store<EntityStore> store,
                                        double maxRange,
                                        double rangeEpsilon) {
        TransformComponent sourceTransform = (TransformComponent) store.getComponent(sourceRef, TransformComponent.getComponentType());
        TransformComponent targetTransform = (TransformComponent) store.getComponent(targetRef, TransformComponent.getComponentType());
        if (sourceTransform == null || targetTransform == null) {
            return false;
        }

        var source = sourceTransform.getPosition();
        var target = targetTransform.getPosition();
        double dx = source.x - target.x;
        double dy = source.y - target.y;
        double dz = source.z - target.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;
        double allowed = maxRange + rangeEpsilon;
        return distanceSq <= allowed * allowed;
    }

    public static boolean hasMovedFrom(@Nonnull Ref<EntityStore> playerRef,
                                       @Nullable Vector3d startPosition,
                                       @Nonnull Store<EntityStore> store,
                                       double cancelMoveDistance) {
        if (startPosition == null) {
            return false;
        }
        TransformComponent transform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        if (transform == null) {
            return true;
        }

        var position = transform.getPosition();
        double dx = position.x - startPosition.x;
        double dz = position.z - startPosition.z;
        double movedSq = dx * dx + dz * dz;
        return movedSq > cancelMoveDistance * cancelMoveDistance;
    }

    public static boolean isConsumableMarkerCandidate(@Nonnull Ref<EntityStore> playerRef,
                                                      @Nonnull Ref<EntityStore> targetRef,
                                                      float executeThreshold,
                                                      @Nonnull Store<EntityStore> store) {
        if (targetRef.equals(playerRef)) {
            return false;
        }
        if (store.getComponent(targetRef, Player.getComponentType()) != null) {
            return false;
        }
        if (store.getComponent(targetRef, NPCMarkerComponent.getComponentType()) == null) {
            return false;
        }
        return isAtOrBelowHealthPercent(targetRef, executeThreshold, store);
    }

    @Nullable
    public static EntityStatValue resolveHealth(@Nonnull Ref<EntityStore> targetRef,
                                                @Nonnull Store<EntityStore> store) {
        EntityStatMap targetStats = (EntityStatMap) store.getComponent(targetRef, EntityStatMap.getComponentType());
        return targetStats != null ? targetStats.get(DefaultEntityStatTypes.getHealth()) : null;
    }
}
