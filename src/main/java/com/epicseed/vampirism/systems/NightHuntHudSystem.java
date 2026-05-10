package com.epicseed.vampirism.systems;

import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.epiccore.vampirism.domain.hunt.NightHuntStatusSnapshot;
import com.epicseed.epiccore.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.hud.NightHuntHudService;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntHudSystem extends EntityTickingSystem<EntityStore> {

    private final NightHuntService nightHuntService;

    public NightHuntHudSystem(@Nonnull NightHuntService nightHuntService) {
        this.nightHuntService = nightHuntService;
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.of(Player.getComponentType());
    }

    @Override
    public void tick(float dt,
                     int index,
                     @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store,
                     @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        @SuppressWarnings("unchecked")
        Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);
        Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null) {
            return;
        }
        if (playerRefComponent == null) {
            NightHuntHudService.hide(playerRef, player, null);
            return;
        }
        UUID uuid = playerRefComponent.getUuid();
        if (!VampirismClassifications.isVampiric(uuid)) {
            NightHuntHudService.hide(playerRef, player, playerRefComponent);
            return;
        }
        NightHuntStatusSnapshot snapshot = nightHuntService.getStatusSnapshot(uuid);
        NightHuntHudService.sync(playerRef, player, playerRefComponent, snapshot.active() ? snapshot : null);
    }
}
