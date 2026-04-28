package com.epicseed.vampirism.systems;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.modifier.ContextKey;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Tracks which players are currently crouching/sneaking.
 *
 * <p>Each ECS tick the system reads {@link MovementStatesComponent#getMovementStates()}
 * and syncs the static {@code sneakingPlayers} set so that
 * {@link #isSneaking(UUID)} is safe to call from any modifier context.
 */
public class SneakSystem extends EntityTickingSystem<EntityStore> {

    private static final Set<UUID> sneakingPlayers = ConcurrentHashMap.newKeySet();

    /** Context key for "is the player sneaking" — used by {@code ModifierContext.resolve()}. */
    public static final ContextKey<Boolean> IS_SNEAKING = new ContextKey<>() {};

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the player is currently crouching/sneaking. */
    public static boolean isSneaking(@Nonnull UUID uuid) {
        return sneakingPlayers.contains(uuid);
    }

    /** Removes a player's sneak state — call on disconnect to prevent stale entries. */
    public static void clearPlayer(@Nonnull UUID uuid) {
        sneakingPlayers.remove(uuid);
    }

    // -------------------------------------------------------------------------
    // ECS tick
    // -------------------------------------------------------------------------

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> ref = (Ref<EntityStore>) chunk.getReferenceTo(index);

        if (store.getComponent(ref, Player.getComponentType()) == null) return;

        PlayerRef playerRefComp = (PlayerRef) store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRefComp == null) return;

        MovementStatesComponent msc = (MovementStatesComponent) store.getComponent(
                ref, MovementStatesComponent.getComponentType());

        UUID uuid = playerRefComp.getUuid();

        if (msc == null) {
            sneakingPlayers.remove(uuid);
            return;
        }

        MovementStates states = msc.getMovementStates();
        if (states != null && states.crouching) {
            sneakingPlayers.add(uuid);
        } else {
            sneakingPlayers.remove(uuid);
        }
    }
}
