package com.epicseed.vampirism.skill.runtime;

import java.util.Collection;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.runtime.PlayerRuntimeIndex;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Lightweight index of active player entity refs keyed by UUID.
 *
 * <p>Populated each tick by tick-based systems (e.g. {@link com.epicseed.vampirism.systems.VampireVitalitySystem})
 * so that ability resolvers can perform spatial queries without requiring a full ECS archetype
 * iteration. Entries are removed when a player leaves the world.
 *
 * <p>This class delegates to the shared player runtime index.
 */
public final class EntityRefTracker {

    private EntityRefTracker() {}

    /** Register or refresh a player's entity ref. Call once per tick from a player-iterating system. */
    public static void register(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref) {
        PlayerRuntimeIndex.register(uuid, ref);
    }

    /** Remove a player's ref when they leave the world. */
    public static void unregister(@Nonnull UUID uuid) {
        PlayerRuntimeIndex.unregister(uuid);
    }

    /** Returns an unmodifiable snapshot view of all currently tracked refs. */
    @Nonnull
    public static Collection<Ref<EntityStore>> getAll() {
        return PlayerRuntimeIndex.getAll();
    }

    /** Returns the entity ref for the given UUID, or {@code null} if not tracked. */
    @Nullable
    public static Ref<EntityStore> get(@Nonnull UUID uuid) {
        return PlayerRuntimeIndex.get(uuid);
    }

    /** Returns the number of currently tracked entities. */
    public static int size() {
        return PlayerRuntimeIndex.size();
    }
}
