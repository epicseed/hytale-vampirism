package com.epicseed.vampirism.hud;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.MultipleHudAdapter;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RitualHudService {

    private static final String RITUAL_HUD_KEY = "vampiric_ritual_status";

    private static final Map<Ref<EntityStore>, RitualStatusHud> HUDS = new ConcurrentHashMap<>();

    private static volatile Function<PlayerRef, RitualStatusHud> hudFactory;

    private RitualHudService() {
    }

    public static void init(@Nonnull Function<PlayerRef, RitualStatusHud> hudFactory) {
        RitualHudService.hudFactory = Objects.requireNonNull(hudFactory, "hudFactory");
    }

    public static void sync(@Nonnull Ref<EntityStore> playerRef,
                            @Nonnull Player player,
                            @Nullable PlayerRef playerRefComponent,
                            @Nullable VampiricRitualRuntimeSnapshot snapshot) {
        RitualStatusHud hud = HUDS.get(playerRef);
        if (hud == null) {
            if (snapshot == null || playerRefComponent == null || !MultipleHudAdapter.isAvailable()) {
                return;
            }
            Function<PlayerRef, RitualStatusHud> factory = hudFactory;
            if (factory == null) {
                throw new IllegalStateException("Ritual HUD service not initialized.");
            }
            RitualStatusHud created = Objects.requireNonNull(factory.apply(playerRefComponent), "hudFactory returned null");
            if (!MultipleHudAdapter.setCustomHud(player, playerRefComponent, RITUAL_HUD_KEY, created)) {
                return;
            }
            RitualStatusHud existing = HUDS.putIfAbsent(playerRef, created);
            hud = existing != null ? existing : created;
        }
        hud.sync(snapshot);
    }

    public static void cleanup(@Nullable Ref<EntityStore> playerRef) {
        if (playerRef == null) {
            return;
        }
        RitualStatusHud removed = HUDS.remove(playerRef);
        if (removed == null) {
            return;
        }
        Store<EntityStore> store = playerRef.getStore();
        if (store == null || !MultipleHudAdapter.isAvailable()) {
            return;
        }
        Player player = store.getComponent(playerRef, Player.getComponentType());
        PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (player == null || playerRefComponent == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Player playerForTask = player;
        PlayerRef playerRefForTask = playerRefComponent;
        CompletableFuture.runAsync(() ->
                MultipleHudAdapter.hideCustomHud(playerForTask, playerRefForTask, RITUAL_HUD_KEY), (Executor) world);
    }
}
