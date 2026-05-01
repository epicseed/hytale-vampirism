package com.epicseed.epiccore.hytale;

import java.lang.reflect.Field;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldMapTracker;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class WorldMapTrackerAdapter {

    private static final Field TRANSFORM_COMPONENT_FIELD = resolveTransformComponentField();

    private WorldMapTrackerAdapter() {
    }

    public static void syncTransform(@Nonnull Holder<EntityStore> holder,
                                     @Nonnull World world,
                                     boolean clear) {
        Runnable action = () -> {
            Player player = holder.getComponent(Player.getComponentType());
            if (player == null) {
                return;
            }
            TransformComponent transform = clear
                    ? null
                    : holder.getComponent(TransformComponent.getComponentType());
            setTransform(player.getWorldMapTracker(), transform);
        };
        if (world.isInThread()) {
            action.run();
            return;
        }
        world.execute(action);
    }

    // SDK-sensitive: WorldMapTracker currently reads this cache from its own map thread.
    private static void setTransform(@Nonnull WorldMapTracker tracker,
                                     TransformComponent transform) {
        try {
            TRANSFORM_COMPONENT_FIELD.set(tracker, transform);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to synchronize WorldMapTracker transform cache.", e);
        }
    }

    @Nonnull
    private static Field resolveTransformComponentField() {
        try {
            Field field = WorldMapTracker.class.getDeclaredField("transformComponent");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
}
