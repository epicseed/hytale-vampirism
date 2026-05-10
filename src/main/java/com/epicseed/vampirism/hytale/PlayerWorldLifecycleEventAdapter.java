package com.epicseed.vampirism.hytale;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.hytale.WorldMapTrackerAdapter;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PlayerWorldLifecycleEventAdapter {

    static final List<String> PLAYER_REMOVAL_FROM_WORLD_EVENT_CLASS_NAMES = List.of(
            "com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent",
            "com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent");

    private static final Logger LOGGER = Logger.getLogger(PlayerWorldLifecycleEventAdapter.class.getName());

    private PlayerWorldLifecycleEventAdapter() {
    }

    public static void registerWorldRemovalEvents(@Nonnull EventRegistry eventRegistry,
                                                  @Nonnull ClassLoader classLoader) {
        List<String> availableEventClasses =
                resolveAvailableEventClassNames(classLoader, PLAYER_REMOVAL_FROM_WORLD_EVENT_CLASS_NAMES);
        if (availableEventClasses.isEmpty()) {
            LOGGER.warning("[PlayerWorldLifecycleEventAdapter] No compatible player removal-from-world event class was found.");
            return;
        }
        for (String className : availableEventClasses) {
            registerWorldRemovalEvent(eventRegistry, classLoader, className);
        }
    }

    @Nonnull
    static List<String> resolveAvailableEventClassNames(@Nonnull ClassLoader classLoader,
                                                        @Nonnull List<String> candidateClassNames) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> resolved = new ArrayList<>();
        for (String className : candidateClassNames) {
            if (className == null || className.isBlank() || !seen.add(className)) {
                continue;
            }
            try {
                Class.forName(className, false, classLoader);
                resolved.add(className);
            } catch (ClassNotFoundException ignored) {
                // Runtime compatibility: some server jars expose only one removal event variant.
            }
        }
        return List.copyOf(resolved);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerWorldRemovalEvent(@Nonnull EventRegistry eventRegistry,
                                                  @Nonnull ClassLoader classLoader,
                                                  @Nonnull String className) {
        try {
            Class<?> eventClass = Class.forName(className, false, classLoader);
            Method getHolder = eventClass.getMethod("getHolder");
            Method getWorld = eventClass.getMethod("getWorld");
            eventRegistry.registerGlobal((Class) eventClass, event -> clearWorldMapTracker(event, getHolder, getWorld));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to bind player world lifecycle event: " + className, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void clearWorldMapTracker(@Nonnull Object event,
                                             @Nonnull Method getHolder,
                                             @Nonnull Method getWorld) {
        try {
            Holder<EntityStore> holder = (Holder<EntityStore>) getHolder.invoke(event);
            World world = (World) getWorld.invoke(event);
            if (holder == null || world == null) {
                return;
            }
            WorldMapTrackerAdapter.syncTransform(holder, world, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to process player world lifecycle event: "
                    + event.getClass().getName(), e);
        }
    }
}
