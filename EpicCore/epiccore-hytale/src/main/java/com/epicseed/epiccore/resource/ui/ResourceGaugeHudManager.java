package com.epicseed.epiccore.resource.ui;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.MultipleHudAdapter;
import com.epicseed.epiccore.resource.ResourceGaugeValue;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class ResourceGaugeHudManager<H extends ResourceGaugeHud> {

    private final String hudKey;
    private final Function<PlayerRef, H> hudFactory;
    private final Map<Ref<EntityStore>, H> huds = new ConcurrentHashMap<>();

    public ResourceGaugeHudManager(@Nonnull String hudKey, @Nonnull Function<PlayerRef, H> hudFactory) {
        this.hudKey = Objects.requireNonNull(hudKey, "hudKey");
        this.hudFactory = Objects.requireNonNull(hudFactory, "hudFactory");
    }

    public boolean isActive(@Nonnull Ref<EntityStore> playerRef) {
        return huds.containsKey(playerRef);
    }

    public void sync(@Nonnull Ref<EntityStore> playerRef, int currentValue, int maxValue) {
        H hud = huds.get(playerRef);
        if (hud != null) {
            hud.sync(currentValue, maxValue);
        }
    }

    public void sync(@Nonnull Ref<EntityStore> playerRef,
                     @Nonnull ResourceGaugeValue value,
                     boolean alternateMode) {
        H hud = huds.get(playerRef);
        if (hud != null) {
            hud.sync(value.currentValue(), value.maxValue(), alternateMode);
        }
    }

    public void sync(@Nonnull Ref<EntityStore> playerRef, int currentValue, int maxValue, boolean alternateMode) {
        sync(playerRef, new ResourceGaugeValue(currentValue, maxValue), alternateMode);
    }

    public void syncAlternateMode(@Nonnull Ref<EntityStore> playerRef, boolean alternateMode) {
        H hud = huds.get(playerRef);
        if (hud != null) {
            hud.syncAlternateMode(alternateMode);
        }
    }

    public boolean ensureInstalled(@Nonnull Ref<EntityStore> playerRef,
                                   @Nonnull Player player,
                                   @Nullable PlayerRef playerRefComponent,
                                   @Nonnull ResourceGaugeValue value,
                                   boolean alternateMode) {
        if (!MultipleHudAdapter.isAvailable() || playerRefComponent == null) {
            return false;
        }
        H existing = huds.get(playerRef);
        if (existing != null) {
            existing.sync(value.currentValue(), value.maxValue(), alternateMode);
            return true;
        }

        H hud = Objects.requireNonNull(hudFactory.apply(playerRefComponent), "hudFactory returned null");
        boolean installed = MultipleHudAdapter.setCustomHud(player, playerRefComponent, hudKey, hud);
        if (!installed) {
            return false;
        }
        huds.put(playerRef, hud);
        hud.sync(value.currentValue(), value.maxValue(), alternateMode);
        return true;
    }

    public void cleanup(@Nonnull Ref<EntityStore> playerRef,
                        @Nonnull Player player,
                        @Nullable PlayerRef playerRefComponent) {
        H removed = huds.remove(playerRef);
        if (removed == null || !MultipleHudAdapter.isAvailable() || playerRefComponent == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Player playerForTask = player;
        PlayerRef playerRefForTask = playerRefComponent;
        CompletableFuture.runAsync(() ->
                MultipleHudAdapter.hideCustomHud(playerForTask, playerRefForTask, hudKey), (Executor) world);
    }
}
