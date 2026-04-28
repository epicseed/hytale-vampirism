package com.epicseed.vampirism.systems;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.epicseed.vampirism.domain.blood.BloodService;
import com.epicseed.vampirism.domain.blood.BloodState;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.hud.BloodGaugeHud;
import com.epicseed.vampirism.hud.RelicCooldownHud;
import com.epicseed.vampirism.hytale.MultipleHudAdapter;
import com.epicseed.vampirism.ui.RelicBindingsUI;
import com.epicseed.vampirism.ui.SkillTreeUI;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.epicseed.vampirism.modifier.ContextKey;
import com.epicseed.vampirism.modifier.ModifierTag;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.relic.RelicInventoryService;
import com.epicseed.vampirism.skill.runtime.EntityRefTracker;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.interface_.HudComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class VampireVitalitySystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final String BLOOD_GAUGE_HUD_KEY = "vampiric_blood_gauge";
    private static final String RELIC_COOLDOWN_HUD_KEY = "vampiric_relic_cooldowns";
    private static final String VAMPIRE_THRONE_BLOCK_ID = "VampireThrone";
    private static final float VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS = 2.5f;
    public static final int BASE_BLOOD_CAPACITY_UNITS = BloodService.BASE_BLOOD_CAPACITY_UNITS;

    private static final Map<Ref<EntityStore>, BloodGaugeHud> bloodGaugeHuds = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, RelicCooldownHud> relicCooldownHuds = new ConcurrentHashMap<>();

    /**
     * Called when a vampire lands a killing blow. Grants blood, kill heal bonus, and may trigger infection.
     */
    public static void onPlayerKill(@Nonnull Ref<EntityStore> attackerRef,
                                    @Nonnull Store<EntityStore> store,
                                    @Nullable Ref<EntityStore> victimRef,
                                    @Nonnull UUID victimUuid,
                                    @Nonnull String victimName) {
        BloodState state = BloodService.getOrCreate(attackerRef);

        state.maxBlood = BloodService.resolveCapacityUnits(attackerRef, store);
        state.blood = Math.min(state.maxBlood, state.blood + VampirismConfig.get().getSatietyPerKill());

        // Kill heal bonus
        float healBonus = VampirismConfig.get().getKillHealBonus();
        if (healBonus > 0f) {
            EntityStatMap stats = (EntityStatMap) store.getComponent(attackerRef, EntityStatMap.getComponentType());
            if (stats != null) {
                stats.addStatValue(DefaultEntityStatTypes.getHealth(), healBonus);
            }
        }

        // Infection on kill
        VampirismConfig cfg = VampirismConfig.get();
        if (cfg.isInfectionEnabled() && Math.random() <= cfg.getInfectionChance()) {
            VampireInfectionSystem.beginInfection(
                    victimUuid,
                    victimName,
                    victimRef,
                    store,
                    "A vampiric infection takes hold of your body.");
        }

        BloodGaugeHud hud = bloodGaugeHuds.get(attackerRef);
        if (hud != null) {
            hud.syncBlood(state.blood, state.maxBlood);
        }
        LOGGER.atInfo().log("[Satiety] Kill registered! Blood: " + state.blood + "/" + state.maxBlood);
    }

    public static boolean isStarving(@Nonnull Ref<EntityStore> playerRef) {
        return BloodService.isStarving(playerRef);
    }

    public static int getBlood(@Nonnull Ref<EntityStore> playerRef) {
        return BloodService.getBlood(playerRef);
    }

    public static int getMaxBlood(@Nonnull Ref<EntityStore> playerRef) {
        return BloodService.getMaxBlood(playerRef);
    }

    public static boolean canAffordBlood(@Nonnull Ref<EntityStore> playerRef, int bloodCost) {
        return BloodService.canAffordBlood(playerRef, bloodCost);
    }

    public static void spendBlood(@Nonnull Ref<EntityStore> playerRef, int bloodCost) {
        if (bloodCost <= 0) return;
        BloodState state = BloodService.spendBlood(playerRef, bloodCost);
        BloodGaugeHud hud = bloodGaugeHuds.get(playerRef);
        if (hud != null) hud.syncBlood(state.blood, state.maxBlood);
    }

    public static int addBlood(@Nonnull Ref<EntityStore> playerRef, int bloodGain) {
        if (bloodGain <= 0) {
            return getBlood(playerRef);
        }
        BloodState state = BloodService.addBlood(playerRef, bloodGain);
        BloodGaugeHud hud = bloodGaugeHuds.get(playerRef);
        if (hud != null) hud.syncBlood(state.blood, state.maxBlood);
        return state.blood;
    }

    public static int addBloodByUuid(@Nonnull UUID uuid, int bloodGain) {
        Ref<EntityStore> ref = BloodService.getRefByUuid(uuid);
        if (ref == null) return -1;
        return addBlood(ref, bloodGain);
    }

    public static Ref<EntityStore> getRefByUuid(@Nonnull UUID uuid) {
        return BloodService.getRefByUuid(uuid);
    }

    public static int getBloodByUuid(@Nonnull UUID uuid) {
        return BloodService.getBloodByUuid(uuid);
    }

    public static int getMaxBloodByUuid(@Nonnull UUID uuid) {
        return BloodService.getMaxBloodByUuid(uuid);
    }

    public static boolean isStarvingByUuid(@Nonnull UUID uuid) {
        return BloodService.isStarvingByUuid(uuid);
    }

    public static boolean isHudActiveByUuid(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = BloodService.getRefByUuid(uuid);
        return ref != null && bloodGaugeHuds.containsKey(ref);
    }

    public static void captureDisconnectState(@Nonnull UUID uuid) {
        BloodService.captureDisconnectState(uuid);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

            Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;

            CustomUIPage activeCustomPage = player.getPageManager().getCustomPage();
            boolean relicBindingsOpen = activeCustomPage instanceof RelicBindingsUI;
            boolean skillTreeOpen = activeCustomPage instanceof SkillTreeUI;
            boolean blockRelicHudRefresh = activeCustomPage != null;

            PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComponent != null && relicBindingsOpen) {
                RelicBindingsUI.refreshOpenCooldowns(playerRefComponent.getUuid());
            }
            if (playerRefComponent == null) {
                return;
            }
            boolean creativeMode = player.getGameMode() == GameMode.Creative;
            boolean isVampire = VampireStatusRegistry.get().isVampire(playerRefComponent.getUuid());

            if (!isVampire) {
                RelicInventoryService.syncOwnership(playerRef, store, false);
                cleanupPlayer(playerRef, player, playerRefComponent);
                return;
            }

            BloodService.registerRef(playerRefComponent.getUuid(), playerRef);
            EntityRefTracker.register(playerRefComponent.getUuid(), playerRef);

            BloodState state = BloodService.getOrCreateLoaded(playerRef, playerRefComponent.getUuid(), store);

            RelicInventoryService.SyncResult relicSync = RelicInventoryService.syncOwnership(
                    playerRef,
                    store,
                    true,
                    !RelicInventoryService.isAutoGrantSuppressed(playerRef));
            if (relicSync.inventoryFull()) {
                if (!state.relicInventoryFullNotified) {
                    String message = relicSync.firstStrandedLocation() != null
                            ? "Your Vampirism Relic is stuck in " + relicSync.firstStrandedLocation().describe()
                            + ". Free a visible inventory slot and use /vampirismrelic get."
                            : "Your inventory is full, so your Vampirism Relic could not be returned. Free a visible inventory slot and use /vampirismrelic get.";
                    player.sendMessage(Message.raw(message).color("yellow"));
                    state.relicInventoryFullNotified = true;
                }
            } else {
                state.relicInventoryFullNotified = false;
            }

            BloodService.refreshCapacity(state, playerRef, store);

            long now = System.currentTimeMillis();

            // HUD initialization
            if ((!bloodGaugeHuds.containsKey(playerRef) || !relicCooldownHuds.containsKey(playerRef)) && !state.hudInitFailed
                    && now - state.firstSeenTime >= VampirismConfig.get().getHudInitDelayMs()) {
                state.hudInitFailed = true;
                initHuds(playerRef, player, playerRefComponent, store, state);
            }

            RelicCooldownHud relicHud = relicCooldownHuds.get(playerRef);
            long cooldownHudInterval = VampirismConfig.get().getCooldownHudUpdateIntervalMs();
            boolean relicInHand = isRelicInHand(playerRef, store);
            syncInputBindingsHud(player, playerRefComponent, state, relicInHand);
            if (relicHud != null
                    && !blockRelicHudRefresh
                    && !skillTreeOpen
                    && (cooldownHudInterval <= 0L
                    || now - state.lastCooldownHudUpdateTime >= cooldownHudInterval)) {
                state.lastCooldownHudUpdateTime = now;
                relicHud.refresh(playerRef, store);
            }

            BloodGaugeHud hud = bloodGaugeHuds.get(playerRef);
            if (hud != null) {
                hud.syncCreativeMode(creativeMode);
            }

            if (now - state.lastUpdateTime < VampirismConfig.get().getSatietyUpdateIntervalMs()) return;

            float elapsedSeconds = (float)(now - state.lastUpdateTime) / 1000f;
            state.lastUpdateTime = now;

            applyVampireThroneRecovery(playerRef, player, store, state, elapsedSeconds);

            // Starvation check
            if (!state.isStarving && state.blood <= VampirismConfig.get().getSatietyStarvingThreshold()) {
                state.isStarving = true;
                LOGGER.atInfo().log("[Satiety] Player " + playerRef + " is STARVING! (" + state.blood + "/" + state.maxBlood + ")");
            } else if (state.isStarving && state.blood >= VampirismConfig.get().getSatietyRecoveryThreshold()) {
                state.isStarving = false;
                LOGGER.atInfo().log("[Satiety] Player " + playerRef + " recovered from starvation.");
            }

            if (hud != null) {
                hud.sync(state.blood, state.maxBlood, creativeMode);
            }

        } catch (Exception e) {
            LOGGER.atSevere().log("[VampireVitalitySystem] Error: " + e.getMessage());
        }
    }

    private void cleanupPlayer(@Nonnull Ref<EntityStore> playerRef,
                               @Nonnull Player player,
                               PlayerRef playerRefComponent) {
        if (playerRefComponent != null) {
            BloodService.unregisterRef(playerRefComponent.getUuid());
            EntityRefTracker.unregister(playerRefComponent.getUuid());
        }
        bloodGaugeHuds.remove(playerRef);
        relicCooldownHuds.remove(playerRef);
        if (stateInputBindingsHidden(playerRef)) {
            restoreInputBindings(player, playerRefComponent);
        }
        if (MultipleHudAdapter.isAvailable() && playerRefComponent != null) {
            World world = player.getWorld();
            if (world != null) {
                Player p = player;
                PlayerRef pr = playerRefComponent;
                CompletableFuture.runAsync(() -> {
                    MultipleHudAdapter.hideCustomHud(p, pr, BLOOD_GAUGE_HUD_KEY);
                    MultipleHudAdapter.hideCustomHud(p, pr, RELIC_COOLDOWN_HUD_KEY);
                }, (Executor) world);
            }
        }
        BloodService.removeState(playerRef);
    }

    private void syncInputBindingsHud(@Nonnull Player player,
                                       PlayerRef playerRefComponent,
                                       @Nonnull BloodState state,
                                      boolean relicInHand) {
        if (playerRefComponent == null) return;
        if (relicInHand == state.inputBindingsHidden) {
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

    private boolean stateInputBindingsHidden(@Nonnull Ref<EntityStore> playerRef) {
        BloodState state = BloodService.getState(playerRef);
        return state != null && state.inputBindingsHidden;
    }

    private void restoreInputBindings(@Nonnull Player player, PlayerRef playerRefComponent) {
        if (playerRefComponent == null) return;
        player.getHudManager().showHudComponents(playerRefComponent, HudComponent.InputBindings);
    }

    private boolean isRelicInHand(@Nonnull Ref<EntityStore> playerRef,
                                  @Nonnull Store<EntityStore> store) {
        ItemStack stack = InventoryComponent.getItemInHand(store, playerRef);
        return stack != null && RelicInventoryService.RELIC_ITEM_ID.equals(stack.getItemId());
    }

    private void initHuds(@Nonnull Ref<EntityStore> playerRef,
                          @Nonnull Player player,
                           PlayerRef playerRefComponent,
                           @Nonnull Store<EntityStore> store,
                           @Nonnull BloodState state) {
        if (!MultipleHudAdapter.isAvailable() || playerRefComponent == null) return;
        World world = player.getWorld();
        if (world == null) return;

        Ref<EntityStore> pRef = playerRef;
        Player p = player;
        PlayerRef pr = playerRefComponent;

        CompletableFuture.runAsync(() -> {
            boolean bloodGaugeReady = bloodGaugeHuds.containsKey(pRef);
            if (!bloodGaugeReady) {
                BloodGaugeHud hud = new BloodGaugeHud(pr);
                bloodGaugeReady = MultipleHudAdapter.setCustomHud(p, pr, BLOOD_GAUGE_HUD_KEY, hud);
                if (bloodGaugeReady) {
                    bloodGaugeHuds.put(pRef, hud);
                    hud.sync(state.blood, state.maxBlood, p.getGameMode() == GameMode.Creative);
                }
            }

            boolean cooldownReady = relicCooldownHuds.containsKey(pRef);
            if (!cooldownReady) {
                RelicCooldownHud relicHud = new RelicCooldownHud(pr);
                relicHud.primeState(pRef, store);
                cooldownReady = MultipleHudAdapter.setCustomHud(p, pr, RELIC_COOLDOWN_HUD_KEY, relicHud);
                if (cooldownReady) {
                    relicCooldownHuds.put(pRef, relicHud);
                }
            }

            if (bloodGaugeReady && cooldownReady) {
                state.hudInitFailed = false;
                LOGGER.atInfo().log("[Vitality] HUDs initialized for " + pRef);
            } else {
                state.hudInitFailed = false;
                LOGGER.atWarning().log("[Vitality] Failed to init one or more HUDs for " + pRef + "; retrying.");
            }
        }, (Executor) world);
    }

    private void applyVampireThroneRecovery(@Nonnull Ref<EntityStore> playerRef,
                                             @Nonnull Player player,
                                             @Nonnull Store<EntityStore> store,
                                             @Nonnull BloodState state,
                                            float elapsedSeconds) {
        if (!isMountedOnVampireThrone(playerRef, player, store)) {
            state.vampireThroneRecoveryAccumulator = 0f;
            return;
        }
        if (state.blood >= state.maxBlood) {
            state.vampireThroneRecoveryAccumulator = 0f;
            return;
        }

        state.vampireThroneRecoveryAccumulator += elapsedSeconds;
        if (state.vampireThroneRecoveryAccumulator < VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS) {
            return;
        }

        int recoveryTicks = (int) (state.vampireThroneRecoveryAccumulator / VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS);
        state.vampireThroneRecoveryAccumulator -= recoveryTicks * VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS;
        addBlood(playerRef, recoveryTicks * VampirismConfig.get().getVampireThroneRecoveryBlood());
    }

    private boolean isMountedOnVampireThrone(@Nonnull Ref<EntityStore> playerRef,
                                             @Nonnull Player player,
                                             @Nonnull Store<EntityStore> store) {
        MountedComponent mounted = store.getComponent(playerRef, MountedComponent.getComponentType());
        if (mounted == null || mounted.getMountedToBlock() == null || !mounted.getMountedToBlock().isValid()) {
            return false;
        }

        World world = player.getWorld();
        if (world == null) {
            return false;
        }

        ChunkStore chunkStore = world.getChunkStore();
        if (chunkStore == null || chunkStore.getStore() == null) {
            return false;
        }

        BlockMountComponent blockMount;
        try {
            blockMount = chunkStore.getStore().getComponent(
                    mounted.getMountedToBlock(),
                    BlockMountComponent.getComponentType());
        } catch (IllegalStateException ignored) {
            return false;
        }
        if (blockMount == null || blockMount.getExpectedBlockType() == null) {
            return false;
        }

        return VAMPIRE_THRONE_BLOCK_ID.equals(blockMount.getExpectedBlockType().getId());
    }

    /** Context key for "is the player starving" — cached per compute() call. */
    public static final ContextKey<Boolean> IS_STARVING = new ContextKey<>() {};

    /** Context key for "player is overfed" (blood == max). */
    public static final ContextKey<Boolean> IS_OVERFED = new ContextKey<>() {};

    /** Context key for "blood state is normal" (above starving threshold but below overfed). */
    public static final ContextKey<Boolean> IS_BLOOD_STATE_NORMAL = new ContextKey<>() {};

    public static boolean isOverfed(@Nonnull Ref<EntityStore> playerRef) {
        return BloodService.isOverfed(playerRef);
    }

    public static boolean isBloodStateNormal(@Nonnull Ref<EntityStore> playerRef) {
        return BloodService.isBloodStateNormal(playerRef);
    }

    /** Tags for modifiers registered by this system. */
    public enum Tag implements ModifierTag {
        BLOODLUST_DAMAGE, STARVATION_DAMAGE, BLOODLUST_LIFESTEAL;
        @Override public String key() { return "bloodlust:" + name(); }
    }

    /** Registers global modifiers owned by this system. Call once at plugin startup. */
    public static void registerModifiers() {
        ModifierRegistry reg = ModifierRegistry.get();

        reg.registerGlobal(VampireStatType.DAMAGE_OUT, Tag.BLOODLUST_DAMAGE, 20, (current, ctx) -> {
            boolean inSunlight = ctx.resolve(SunburnSystem.IN_SUNLIGHT, () -> SunburnSystem.isInSunlight(ctx.uuid()));
            return !inSunlight ? current * VampirismConfig.get().getBloodlustDamageMultiplier() : current;
        });

        reg.registerGlobal(VampireStatType.DAMAGE_OUT, Tag.STARVATION_DAMAGE, 30, (current, ctx) -> {
            boolean starving = ctx.resolve(IS_STARVING, () -> isStarving(ctx.ref()));
            return starving ? current + VampirismConfig.get().getStarvingDamageBonus() : current;
        });

        reg.registerGlobal(VampireStatType.LIFESTEAL, Tag.BLOODLUST_LIFESTEAL, 10, (current, ctx) -> {
            boolean inSunlight = ctx.resolve(SunburnSystem.IN_SUNLIGHT, () -> SunburnSystem.isInSunlight(ctx.uuid()));
            return !inSunlight ? VampirismConfig.get().getBloodlustLifesteal() : current;
        });
    }
}
