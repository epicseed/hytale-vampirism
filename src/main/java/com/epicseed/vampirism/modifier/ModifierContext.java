package com.epicseed.vampirism.modifier;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Generic player state carrier passed to every {@link StatModifier} during evaluation.
 *
 * <p>Contains only identity fields ({@code uuid}, {@code ref}) and a type-safe lazy cache.
 * Game-specific state (inSunlight, isFrenzy, etc.) is resolved on demand by each modifier
 * using {@link #resolve(ContextKey, Supplier)} — the context has no knowledge of systems.
 *
 * <p>Uses {@link IdentityHashMap} so {@link ContextKey} singletons are looked up by
 * reference equality (no {@code hashCode}/{@code equals} contract needed on the key).
 */
public final class ModifierContext {

    private final UUID uuid;
    private final Ref<EntityStore> ref;
    private final Store<EntityStore> store;
    private final String abilityId;
    private final String effectId;
    private final String passiveId;
    private final String skillId;
    private final Map<ContextKey<?>, Object> cache = new IdentityHashMap<>();

    public ModifierContext(UUID uuid, Ref<EntityStore> ref) {
        this(uuid, ref, null, null, null, null, null);
    }

    public ModifierContext(UUID uuid, Ref<EntityStore> ref, Store<EntityStore> store) {
        this(uuid, ref, store, null, null, null, null);
    }

    public ModifierContext(UUID uuid,
                           Ref<EntityStore> ref,
                           Store<EntityStore> store,
                           String abilityId,
                           String effectId,
                           String passiveId,
                           String skillId) {
        this.uuid = uuid;
        this.ref = ref;
        this.store = store;
        this.abilityId = abilityId;
        this.effectId = effectId;
        this.passiveId = passiveId;
        this.skillId = skillId;
    }

    public UUID uuid() { return uuid; }
    public Ref<EntityStore> ref() { return ref; }
    public Store<EntityStore> store() { return store; }
    public String abilityId() { return abilityId; }
    public String effectId() { return effectId; }
    public String passiveId() { return passiveId; }
    public String skillId() { return skillId; }

    /**
     * Returns the cached value for {@code key}, computing it with {@code supplier} on first access.
     *
     * <p>Uses reference equality on {@code key} — pass the same static final instance to share
     * the cached result across multiple modifiers.
     *
     * @param key      static final instance declared by the owning system
     * @param supplier called at most once per context; must not return {@code null}
     */
    @SuppressWarnings("unchecked")
    public <T> T resolve(ContextKey<T> key, Supplier<T> supplier) {
        return (T) cache.computeIfAbsent(key, k -> supplier.get());
    }
}
