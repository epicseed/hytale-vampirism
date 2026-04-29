package com.epicseed.vampirism.hytale;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class EntityIdentityAdapter {
    private EntityIdentityAdapter() {
    }

    @Nullable
    public static UUID extractPlayerUuid(@Nonnull Ref<EntityStore> playerRef, @Nonnull Store<EntityStore> store) {
        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        return playerRefComponent != null ? playerRefComponent.getUuid() : null;
    }

    @Nullable
    public static UUID extractEntityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComponent = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }
}
