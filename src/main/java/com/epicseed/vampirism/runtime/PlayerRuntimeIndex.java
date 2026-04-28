package com.epicseed.vampirism.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class PlayerRuntimeIndex {
    private static final Map<UUID, Ref<EntityStore>> REFS = new ConcurrentHashMap<>();

    private PlayerRuntimeIndex() {
    }

    public static void register(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> ref) {
        REFS.put(uuid, ref);
    }

    public static void unregister(@Nonnull UUID uuid) {
        REFS.remove(uuid);
    }

    @Nonnull
    public static Collection<Ref<EntityStore>> getAll() {
        return Collections.unmodifiableCollection(REFS.values());
    }

    @Nullable
    public static Ref<EntityStore> get(@Nonnull UUID uuid) {
        return REFS.get(uuid);
    }

    public static int size() {
        return REFS.size();
    }
}
