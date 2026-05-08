package com.epicseed.vampirism.systems;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.domain.ritual.runtime.VampiricRitualCompanionTracker;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualCompanionSystem extends EntityTickingSystem<EntityStore> {

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);
        PlayerRef player = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        UUID uuid = player.getUuid();
        VampiricRitualCompanionTracker.CompanionState expired =
                VampiricRitualCompanionTracker.expire(uuid, System.currentTimeMillis());
        if (expired != null && expired.companionRef().isValid()) {
            commandBuffer.tryRemoveEntity(expired.companionRef(), RemoveReason.REMOVE);
        }
    }
}
