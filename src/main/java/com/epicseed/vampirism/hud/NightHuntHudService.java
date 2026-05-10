package com.epicseed.vampirism.hud;

import java.util.Objects;
import java.util.function.Function;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.hud.HudBackendResolver;
import com.epicseed.epiccore.hytale.hud.HudSurfaceController;
import com.epicseed.epiccore.hytale.hud.HudSurfacePresenter;
import com.epicseed.epiccore.hytale.hud.HudTitleFrame;
import com.epicseed.epiccore.hytale.hud.SingleSlotHudCoordinator;
import com.epicseed.epiccore.hytale.hud.TitleHudFallback;
import com.epicseed.epiccore.vampirism.domain.hunt.NightHuntStatusSnapshot;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class NightHuntHudService {

    private static final int HUNT_HUD_PRIORITY = 90;
    private static final String HUNT_HUD_KEY = "night_hunt_status";

    private static volatile HudSurfaceController<NightHuntStatusSnapshot, NightHuntStatusHud> controller;

    private NightHuntHudService() {
    }

    public static void init(@Nonnull HudBackendResolver hudBackendResolver,
                            @Nonnull SingleSlotHudCoordinator singleSlotHudCoordinator,
                            @Nonnull Function<PlayerRef, NightHuntStatusHud> hudFactory) {
        Objects.requireNonNull(hudBackendResolver, "hudBackendResolver");
        Objects.requireNonNull(singleSlotHudCoordinator, "singleSlotHudCoordinator");
        Objects.requireNonNull(hudFactory, "hudFactory");
        controller = new HudSurfaceController<>(
                HUNT_HUD_KEY,
                HUNT_HUD_PRIORITY,
                hudBackendResolver,
                singleSlotHudCoordinator,
                new HudSurfacePresenter<>() {
                    @Override
                    @Nonnull
                    public NightHuntStatusHud createHud(@Nonnull PlayerRef playerRef) {
                        return Objects.requireNonNull(hudFactory.apply(playerRef), "hudFactory returned null");
                    }

                    @Override
                    public void syncHud(@Nonnull NightHuntStatusHud hud, @Nullable NightHuntStatusSnapshot state) {
                        hud.sync(state);
                    }

                    @Override
                    @Nullable
                    public HudTitleFrame titleFrame(@Nullable NightHuntStatusSnapshot state) {
                        if (!shouldDisplay(state)) {
                            return null;
                        }
                        NightHuntHudPresentation.DisplayState displayState = NightHuntHudPresentation.present(state);
                        return displayState.visible()
                                ? new HudTitleFrame(
                                displayState.title(),
                                displayState.phase() + " · " + displayState.progress(),
                                false)
                                : null;
                    }
                },
                new TitleHudFallback());
    }

    public static void sync(@Nonnull Ref<EntityStore> playerRef,
                            @Nonnull Player player,
                            @Nullable PlayerRef playerRefComponent,
                            @Nullable NightHuntStatusSnapshot snapshot) {
        if (!shouldDisplay(snapshot)) {
            controller().cleanup(playerRef, player, playerRefComponent);
            return;
        }
        controller().ensureDisplayed(playerRef, player, playerRefComponent, snapshot);
    }

    public static void hide(@Nullable Ref<EntityStore> playerRef,
                            @Nonnull Player player,
                            @Nullable PlayerRef playerRefComponent) {
        if (playerRef == null) {
            return;
        }
        controller().cleanup(playerRef, player, playerRefComponent);
    }

    public static void cleanup(@Nullable Ref<EntityStore> playerRef) {
        if (playerRef == null) {
            return;
        }
        controller().evict(playerRef);
    }

    @Nonnull
    private static HudSurfaceController<NightHuntStatusSnapshot, NightHuntStatusHud> controller() {
        HudSurfaceController<NightHuntStatusSnapshot, NightHuntStatusHud> hudController = controller;
        if (hudController == null) {
            throw new IllegalStateException("Night hunt HUD service not initialized.");
        }
        return hudController;
    }

    private static boolean shouldDisplay(@Nullable NightHuntStatusSnapshot snapshot) {
        return snapshot != null
                && snapshot.active()
                && !"approaching".equals(snapshot.phase());
    }
}
