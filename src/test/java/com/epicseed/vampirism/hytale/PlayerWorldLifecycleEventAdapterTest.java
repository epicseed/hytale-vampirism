package com.epicseed.vampirism.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PlayerWorldLifecycleEventAdapterTest {

    @Test
    void resolvesAvailableRemovalEventsInPreferredOrder() {
        List<String> resolved = PlayerWorldLifecycleEventAdapter.resolveAvailableEventClassNames(
                getClass().getClassLoader(),
                PlayerWorldLifecycleEventAdapter.PLAYER_REMOVAL_FROM_WORLD_EVENT_CLASS_NAMES);

        assertEquals(
                List.of(
                        "com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent",
                        "com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent"),
                resolved);
    }

    @Test
    void resolvesOnlyDrainEventWhenLegacyRemovedEventIsUnavailable() {
        ClassLoader classLoader = new FilteringClassLoader(
                getClass().getClassLoader(),
                Set.of("com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent"));

        List<String> resolved = PlayerWorldLifecycleEventAdapter.resolveAvailableEventClassNames(
                classLoader,
                PlayerWorldLifecycleEventAdapter.PLAYER_REMOVAL_FROM_WORLD_EVENT_CLASS_NAMES);

        assertEquals(List.of("com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent"), resolved);
    }

    @Test
    void resolvesOnlyLegacyRemovedEventWhenDrainEventIsUnavailable() {
        ClassLoader classLoader = new FilteringClassLoader(
                getClass().getClassLoader(),
                Set.of("com.hypixel.hytale.server.core.event.events.player.DrainPlayerFromWorldEvent"));

        List<String> resolved = PlayerWorldLifecycleEventAdapter.resolveAvailableEventClassNames(
                classLoader,
                PlayerWorldLifecycleEventAdapter.PLAYER_REMOVAL_FROM_WORLD_EVENT_CLASS_NAMES);

        assertEquals(List.of("com.hypixel.hytale.server.core.event.events.player.RemovedPlayerFromWorldEvent"), resolved);
    }

    private static final class FilteringClassLoader extends ClassLoader {
        private final Set<String> blockedClassNames;

        private FilteringClassLoader(ClassLoader parent, Set<String> blockedClassNames) {
            super(parent);
            this.blockedClassNames = blockedClassNames;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (blockedClassNames.contains(name)) {
                throw new ClassNotFoundException(name);
            }
            return super.loadClass(name, resolve);
        }
    }
}
