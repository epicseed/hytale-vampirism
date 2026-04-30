package com.epicseed.vampirism.domain.blood;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.MultipleHudAdapter;
import com.epicseed.epiccore.resource.ResourceGaugeValue;
import com.epicseed.epiccore.resource.ui.ResourceGaugeHudManager;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.hud.BloodGaugeHud;
import com.epicseed.vampirism.hud.RelicCooldownHud;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BloodHudService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String BLOOD_GAUGE_HUD_KEY = "vampiric_blood_gauge";
    private static final String RELIC_COOLDOWN_HUD_KEY = "vampiric_relic_cooldowns";

    private static final ResourceGaugeHudManager<BloodGaugeHud> bloodGaugeHudManager =
            new ResourceGaugeHudManager<>(BLOOD_GAUGE_HUD_KEY, BloodGaugeHud::new);
    private static final Map<Ref<EntityStore>, RelicCooldownHud> relicCooldownHuds = new ConcurrentHashMap<>();

    private BloodHudService() {
    }

    public static boolean isHudActiveByUuid(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = BloodService.getRefByUuid(uuid);
        return ref != null && bloodGaugeHudManager.isActive(ref);
    }

    public static void syncBlood(@Nonnull Ref<EntityStore> playerRef, @Nonnull BloodState state) {
        bloodGaugeHudManager.sync(playerRef, state.blood, state.maxBlood);
    }

    public static void syncBloodGauge(@Nonnull Ref<EntityStore> playerRef,
                                      @Nonnull BloodState state,
                                      boolean creativeMode) {
        bloodGaugeHudManager.sync(playerRef, state.blood, state.maxBlood, creativeMode);
    }

    public static void syncCreativeMode(@Nonnull Ref<EntityStore> playerRef, boolean creativeMode) {
        bloodGaugeHudManager.syncAlternateMode(playerRef, creativeMode);
    }

    public static void tryInitialize(@Nonnull Ref<EntityStore> playerRef,
                                     @Nonnull Player player,
                                     @Nullable PlayerRef playerRefComponent,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull BloodState state,
                                     long now) {
        if ((bloodGaugeHudManager.isActive(playerRef) && relicCooldownHuds.containsKey(playerRef))
                || state.hudInitFailed
                || now - state.firstSeenTime < VampirismConfig.get().getHudInitDelayMs()) {
            return;
        }
        state.hudInitFailed = true;
        initialize(playerRef, player, playerRefComponent, store, state);
    }

    public static void refreshRelicCooldowns(@Nonnull Ref<EntityStore> playerRef,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull BloodState state,
                                             long now,
                                             boolean blockRefresh,
                                             boolean skillTreeOpen) {
        RelicCooldownHud relicHud = relicCooldownHuds.get(playerRef);
        long interval = VampirismConfig.get().getCooldownHudUpdateIntervalMs();
        if (relicHud != null
                && !blockRefresh
                && !skillTreeOpen
                && (interval <= 0L || now - state.lastCooldownHudUpdateTime >= interval)) {
            state.lastCooldownHudUpdateTime = now;
            relicHud.refresh(playerRef, store);
        }
    }

    public static void syncInputBindings(@Nonnull Player player,
                                         @Nullable PlayerRef playerRefComponent,
                                         @Nonnull BloodState state,
                                         boolean relicInHand) {
        if (playerRefComponent == null || relicInHand == state.inputBindingsHidden) {
            return;
        }
        if (relicInHand) {
            player.getHudManager().hideHudComponents(playerRefComponent, HudComponent.InputBindings);
            state.inputBindingsHidden = true;
        } else {
            player.getHudManager().showHudComponents(playerRefComponent, HudComponent.InputBindings);
            state.inputBindingsHidden = false;
        }
    }

    public static void cleanup(@Nonnull Ref<EntityStore> playerRef,
                               @Nonnull Player player,
                               @Nullable PlayerRef playerRefComponent,
                               boolean inputBindingsHidden) {
        bloodGaugeHudManager.cleanup(playerRef, player, playerRefComponent);
        relicCooldownHuds.remove(playerRef);
        if (inputBindingsHidden && playerRefComponent != null) {
            player.getHudManager().showHudComponents(playerRefComponent, HudComponent.InputBindings);
        }
        if (!MultipleHudAdapter.isAvailable() || playerRefComponent == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Player playerForTask = player;
        PlayerRef refForTask = playerRefComponent;
        CompletableFuture.runAsync(() -> {
            MultipleHudAdapter.hideCustomHud(playerForTask, refForTask, RELIC_COOLDOWN_HUD_KEY);
        }, (Executor) world);
    }

    private static void initialize(@Nonnull Ref<EntityStore> playerRef,
                                   @Nonnull Player player,
                                   @Nullable PlayerRef playerRefComponent,
                                   @Nonnull Store<EntityStore> store,
                                   @Nonnull BloodState state) {
        if (!MultipleHudAdapter.isAvailable() || playerRefComponent == null) return;
        World world = player.getWorld();
        if (world == null) return;

        Ref<EntityStore> refForTask = playerRef;
        Player playerForTask = player;
        PlayerRef playerRefForTask = playerRefComponent;

        CompletableFuture.runAsync(() -> {
            boolean bloodGaugeReady = bloodGaugeHudManager.ensureInstalled(
                    refForTask,
                    playerForTask,
                    playerRefForTask,
                    new ResourceGaugeValue(state.blood, state.maxBlood),
                    playerForTask.getGameMode() == GameMode.Creative);

            boolean cooldownReady = relicCooldownHuds.containsKey(refForTask);
            if (!cooldownReady) {
                RelicCooldownHud relicHud = new RelicCooldownHud(playerRefForTask);
                relicHud.primeState(refForTask, store);
                cooldownReady = MultipleHudAdapter.setCustomHud(
                        playerForTask, playerRefForTask, RELIC_COOLDOWN_HUD_KEY, relicHud);
                if (cooldownReady) {
                    relicCooldownHuds.put(refForTask, relicHud);
                }
            }

            state.hudInitFailed = false;
            if (bloodGaugeReady && cooldownReady) {
                LOGGER.atInfo().log("[Vitality] HUDs initialized for " + refForTask);
            } else {
                LOGGER.atWarning().log("[Vitality] Failed to init one or more HUDs for " + refForTask + "; retrying.");
            }
        }, (Executor) world);
    }
}
