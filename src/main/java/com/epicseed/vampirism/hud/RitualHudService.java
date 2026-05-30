package com.epicseed.vampirism.hud;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.hud.HudBackendResolver;
import com.epicseed.epiccore.hytale.hud.HudSurfaceController;
import com.epicseed.epiccore.hytale.hud.HudSurfacePresenter;
import com.epicseed.epiccore.hytale.hud.HudTitleFrame;
import com.epicseed.epiccore.hytale.hud.SingleSlotHudCoordinator;
import com.epicseed.epiccore.hytale.hud.TitleHudFallback;
import com.epicseed.epiccore.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.domain.player.VampirismUxPreferenceKeys;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class RitualHudService {

    private static final int RITUAL_HUD_PRIORITY = 100;
    private static final String RITUAL_HUD_KEY = "vampiric_ritual_status";

    private static final Map<Ref<EntityStore>, RitualHudDisplayMode> DISPLAY_MODES = new ConcurrentHashMap<>();

    private static volatile HudSurfaceController<RitualHudState, RitualStatusHud> controller;

    private RitualHudService() {
    }

    public static void init(@Nonnull HudBackendResolver hudBackendResolver,
                            @Nonnull SingleSlotHudCoordinator singleSlotHudCoordinator,
                            @Nonnull BiFunction<PlayerRef, String, RitualStatusHud> hudFactory) {
        Objects.requireNonNull(hudBackendResolver, "hudBackendResolver");
        Objects.requireNonNull(singleSlotHudCoordinator, "singleSlotHudCoordinator");
        Objects.requireNonNull(hudFactory, "hudFactory");
        controller = new HudSurfaceController<>(
                RITUAL_HUD_KEY,
                RITUAL_HUD_PRIORITY,
                hudBackendResolver,
                singleSlotHudCoordinator,
                new HudSurfacePresenter<>() {
                    @Override
                    @Nonnull
                    public RitualStatusHud createHud(@Nonnull PlayerRef playerRef, @Nonnull String hudKey) {
                        return Objects.requireNonNull(hudFactory.apply(playerRef, hudKey), "hudFactory returned null");
                    }

                    @Override
                    public void syncHud(@Nonnull RitualStatusHud hud, @Nullable RitualHudState state) {
                        if (state == null) {
                            hud.sync(null, RitualHudDisplayMode.MINIMAL);
                            return;
                        }
                        hud.sync(state.snapshot(), state.displayMode());
                    }

                    @Override
                    @Nullable
                    public HudTitleFrame titleFrame(@Nullable RitualHudState state) {
                        return state == null ? null : renderTitle(state);
                    }
                },
                new TitleHudFallback());
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
        if (playerRefComponent != null && !isRitualHudVisible(playerRefComponent.getUuid())) {
            controller().cleanup(playerRef, player, playerRefComponent);
            return;
        }
        RitualHudDisplayMode effectiveDisplayMode = displayMode != null
                ? displayMode
                : DISPLAY_MODES.getOrDefault(playerRef, RitualHudDisplayMode.MINIMAL);
        if (displayMode != null) {
            DISPLAY_MODES.put(playerRef, effectiveDisplayMode);
        }
        controller().ensureDisplayed(playerRef, player, playerRefComponent, new RitualHudState(snapshot, effectiveDisplayMode));
    }

    public static void setDisplayMode(@Nonnull Ref<EntityStore> playerRef,
                                      @Nonnull RitualHudDisplayMode displayMode) {
        DISPLAY_MODES.put(playerRef, Objects.requireNonNull(displayMode, "displayMode"));
    }

    public static void clearDisplayMode(@Nullable Ref<EntityStore> playerRef) {
        if (playerRef == null) {
            return;
        }
        DISPLAY_MODES.remove(playerRef);
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
        DISPLAY_MODES.remove(playerRef);
        // Disconnect cleanup may run off the world thread, so it must not
        // resolve ECS components from the player ref just to hide the HUD.
        controller().evict(playerRef);
    }

    @Nonnull
    private static HudSurfaceController<RitualHudState, RitualStatusHud> controller() {
        HudSurfaceController<RitualHudState, RitualStatusHud> hudController = controller;
        if (hudController == null) {
            throw new IllegalStateException("Ritual HUD service not initialized.");
        }
        return hudController;
    }

    private static boolean isRitualHudVisible(@Nonnull java.util.UUID uuid) {
        return !PlayerSkillRegistry.isInitialized()
                || PlayerSkillRegistry.get().isHudVisible(uuid, VampirismUxPreferenceKeys.RITUAL_STATUS_HUD);
    }

    @Nullable
    private static HudTitleFrame renderTitle(@Nonnull RitualHudState state) {
        RitualHudPresentation.DisplayState displayState =
                RitualHudPresentation.present(state.snapshot(), state.displayMode());
        if (!displayState.visible()) {
            return null;
        }
        String secondary = displayState.progress().isBlank()
                ? displayState.guidance()
                : displayState.phase() + " · " + displayState.progress();
        return new HudTitleFrame(displayState.title(), secondary, false);
    }

    private record RitualHudState(@Nullable VampiricRitualRuntimeSnapshot snapshot,
                                  @Nonnull RitualHudDisplayMode displayMode) {
    }
}
