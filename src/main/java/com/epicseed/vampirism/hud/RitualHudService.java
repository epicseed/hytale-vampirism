package com.epicseed.vampirism.hud;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.MultipleHudAdapter;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RitualHudService {

    private static final String RITUAL_HUD_KEY = "vampiric_ritual_status";

    private static final Map<Ref<EntityStore>, RitualStatusHud> HUDS = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, RitualHudDisplayMode> DISPLAY_MODES = new ConcurrentHashMap<>();

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
        sync(playerRef, player, playerRefComponent, snapshot, DISPLAY_MODES.getOrDefault(playerRef, RitualHudDisplayMode.MINIMAL));
    }

    public static void sync(@Nonnull Ref<EntityStore> playerRef,
                            @Nonnull Player player,
                            @Nullable PlayerRef playerRefComponent,
                            @Nullable VampiricRitualRuntimeSnapshot snapshot,
                            @Nullable RitualHudDisplayMode displayMode) {
        RitualHudDisplayMode effectiveDisplayMode = displayMode != null
                ? displayMode
                : DISPLAY_MODES.getOrDefault(playerRef, RitualHudDisplayMode.MINIMAL);
        if (displayMode != null) {
            DISPLAY_MODES.put(playerRef, effectiveDisplayMode);
        }
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
        hud.sync(snapshot, effectiveDisplayMode);
    }

    public static void setDisplayMode(@Nonnull Ref<EntityStore> playerRef,
                                      @Nonnull RitualHudDisplayMode displayMode) {
        RitualHudDisplayMode nextMode = Objects.requireNonNull(displayMode, "displayMode");
        DISPLAY_MODES.put(playerRef, nextMode);
        RitualStatusHud hud = HUDS.get(playerRef);
        if (hud != null) {
            hud.syncDisplayMode(nextMode);
        }
    }

    public static void clearDisplayMode(@Nullable Ref<EntityStore> playerRef) {
        if (playerRef == null) {
            return;
        }
        DISPLAY_MODES.remove(playerRef);
        RitualStatusHud hud = HUDS.get(playerRef);
        if (hud != null) {
            hud.syncDisplayMode(RitualHudDisplayMode.MINIMAL);
        }
    }

    public static void cleanup(@Nullable Ref<EntityStore> playerRef) {
        if (playerRef == null) {
            return;
        }
        DISPLAY_MODES.remove(playerRef);
        // Disconnect cleanup may run off the world thread, so it must not
        // resolve ECS components from the player ref just to hide the HUD.
        HUDS.remove(playerRef);
    }
}
