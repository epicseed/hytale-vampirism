package com.epicseed.vampirism.skill.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight index of active player entity refs keyed by UUID.
 *
 * <p>Populated each tick by tick-based systems (e.g. {@link com.epicseed.vampirism.systems.VampireVitalitySystem})
 * so that ability resolvers can perform spatial queries without requiring a full ECS archetype
 * iteration. Entries are removed when a player leaves the world.
 *
 * <p>This class is thread-safe (backed by a {@link ConcurrentHashMap}).
 */
public final class EntityRefTracker {

    private static final Map<UUID, Ref<EntityStore>> REFS = new ConcurrentHashMap<>();

    private EntityRefTracker() {}

    /** Register or refresh a player's entity ref. Call once per tick from a player-iterating system. */
    public static void register(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref) {
        REFS.put(uuid, ref);
    }

    /** Remove a player's ref when they leave the world. */
    public static void unregister(@Nonnull UUID uuid) {
        REFS.remove(uuid);
    }

    /** Returns an unmodifiable snapshot view of all currently tracked refs. */
    @Nonnull
    public static Collection<Ref<EntityStore>> getAll() {
        return Collections.unmodifiableCollection(REFS.values());
    }

    /** Returns the entity ref for the given UUID, or {@code null} if not tracked. */
    @Nullable
    public static Ref<EntityStore> get(@Nonnull UUID uuid) {
        return REFS.get(uuid);
    }

    /** Returns the number of currently tracked entities. */
    public static int size() {
        return REFS.size();
    }
}
