package com.epicseed.vampirism.systems;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.hud.BloodGaugeHud;
import com.epicseed.vampirism.hud.RelicCooldownHud;
import com.epicseed.vampirism.ui.RelicBindingsUI;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.epicseed.vampirism.modifier.ContextKey;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.ModifierTag;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.relic.RelicInventoryService;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
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
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
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

    private static final boolean MULTIPLE_HUD_AVAILABLE;
    private static final String BLOOD_GAUGE_HUD_KEY = "vampiric_blood_gauge";
    private static final String RELIC_COOLDOWN_HUD_KEY = "vampiric_relic_cooldowns";
    private static final String VAMPIRE_THRONE_BLOCK_ID = "VampireThrone";
    private static final float VAMPIRE_THRONE_REGEN_INTERVAL_SECONDS = 2.5f;
    public static final int BASE_BLOOD_CAPACITY_UNITS = 100;

    static {
        boolean available;
        try {
            Class.forName("com.buuz135.mhud.MultipleHUD");
            available = true;
        } catch (ClassNotFoundException e) {
            available = false;
        }
        MULTIPLE_HUD_AVAILABLE = available;
    }

    private static final Map<Ref<EntityStore>, SatietyState> playerStates = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, BloodGaugeHud> bloodGaugeHuds = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, RelicCooldownHud> relicCooldownHuds = new ConcurrentHashMap<>();
    private static final Map<UUID, Ref<EntityStore>> uuidToRef = new ConcurrentHashMap<>();

    /**
     * Called when a vampire lands a killing blow. Grants blood, kill heal bonus, and may trigger infection.
     */
    public static void onPlayerKill(@Nonnull Ref<EntityStore> attackerRef,
                                    @Nonnull Store<EntityStore> store,
                                    @Nullable Ref<EntityStore> victimRef,
                                    @Nonnull UUID victimUuid,
                                    @Nonnull String victimName) {
        SatietyState state = playerStates.computeIfAbsent(attackerRef, k -> new SatietyState());

        state.maxBlood = resolveBloodCapacityUnits(attackerRef, store);
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
        SatietyState state = playerStates.get(playerRef);
        return state != null && state.isStarving;
    }

    public static int getBlood(@Nonnull Ref<EntityStore> playerRef) {
        SatietyState state = playerStates.get(playerRef);
        return state != null ? state.blood : BASE_BLOOD_CAPACITY_UNITS;
    }

    public static int getMaxBlood(@Nonnull Ref<EntityStore> playerRef) {
        SatietyState state = playerStates.get(playerRef);
        return state != null ? Math.max(1, state.maxBlood) : BASE_BLOOD_CAPACITY_UNITS;
    }

    public static boolean canAffordBlood(@Nonnull Ref<EntityStore> playerRef, int bloodCost) {
        if (bloodCost <= 0) return true;
        SatietyState state = playerStates.get(playerRef);
        int current = state != null ? state.blood : BASE_BLOOD_CAPACITY_UNITS;
        return current >= bloodCost;
    }

    public static void spendBlood(@Nonnull Ref<EntityStore> playerRef, int bloodCost) {
        if (bloodCost <= 0) return;
        SatietyState state = playerStates.computeIfAbsent(playerRef, k -> new SatietyState());
        state.blood = Math.max(0, state.blood - bloodCost);
        BloodGaugeHud hud = bloodGaugeHuds.get(playerRef);
        if (hud != null) hud.syncBlood(state.blood, state.maxBlood);
    }

    public static int addBlood(@Nonnull Ref<EntityStore> playerRef, int bloodGain) {
        if (bloodGain <= 0) {
            return getBlood(playerRef);
        }
        SatietyState state = playerStates.computeIfAbsent(playerRef, k -> new SatietyState());
        state.blood = Math.min(state.maxBlood, state.blood + bloodGain);
        if (state.isStarving && state.blood >= VampirismConfig.get().getSatietyRecoveryThreshold()) {
            state.isStarving = false;
        }
        BloodGaugeHud hud = bloodGaugeHuds.get(playerRef);
        if (hud != null) hud.syncBlood(state.blood, state.maxBlood);
        return state.blood;
    }

    public static int addBloodByUuid(@Nonnull UUID uuid, int bloodGain) {
        Ref<EntityStore> ref = uuidToRef.get(uuid);
        if (ref == null) return -1;
        return addBlood(ref, bloodGain);
    }

    public static Ref<EntityStore> getRefByUuid(@Nonnull UUID uuid) {
        return uuidToRef.get(uuid);
    }

    public static int getBloodByUuid(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = uuidToRef.get(uuid);
        if (ref == null) return -1;
        SatietyState state = playerStates.get(ref);
        return state != null ? state.blood : -1;
    }

    public static int getMaxBloodByUuid(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = uuidToRef.get(uuid);
        if (ref == null) return -1;
        SatietyState state = playerStates.get(ref);
        return state != null ? Math.max(1, state.maxBlood) : -1;
    }

    public static boolean isStarvingByUuid(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = uuidToRef.get(uuid);
        if (ref == null) return false;
        SatietyState state = playerStates.get(ref);
        return state != null && state.isStarving;
    }

    public static boolean isHudActiveByUuid(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = uuidToRef.get(uuid);
        return ref != null && bloodGaugeHuds.containsKey(ref);
    }

    public static void captureDisconnectState(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = uuidToRef.get(uuid);
        if (ref == null) return;
        SatietyState state = playerStates.get(ref);
        if (state == null) return;
        PlayerSkillRegistry.get().setPersistedBlood(uuid, state.blood);
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return null;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(float dt, int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
                     @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer) {
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> playerRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

            Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
            if (player == null) return;

            PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComponent != null) {
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

            uuidToRef.put(playerRefComponent.getUuid(), playerRef);
            EntityRefTracker.register(playerRefComponent.getUuid(), playerRef);

            SatietyState state = playerStates.computeIfAbsent(playerRef, k -> {
                SatietyState loaded = new SatietyState();
                if (playerRefComponent != null) {
                    loaded.blood = PlayerSkillRegistry.get().getPersistedBlood(playerRefComponent.getUuid());
                }
                loaded.maxBlood = resolveBloodCapacityUnits(playerRef, store);
                loaded.blood = Math.max(0, Math.min(loaded.maxBlood, loaded.blood));
                loaded.isStarving = loaded.blood <= VampirismConfig.get().getSatietyStarvingThreshold();
                LOGGER.atInfo().log("[Satiety] Initialized for player " + playerRef);
                return loaded;
            });

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

            state.maxBlood = resolveBloodCapacityUnits(playerRef, store);
            if (state.blood > state.maxBlood) {
                state.blood = state.maxBlood;
            }

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
            if (relicHud != null && (cooldownHudInterval <= 0L
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
            uuidToRef.remove(playerRefComponent.getUuid());
            EntityRefTracker.unregister(playerRefComponent.getUuid());
        }
        bloodGaugeHuds.remove(playerRef);
        relicCooldownHuds.remove(playerRef);
        if (stateInputBindingsHidden(playerRef)) {
            restoreInputBindings(player, playerRefComponent);
        }
        if (MULTIPLE_HUD_AVAILABLE && playerRefComponent != null) {
            World world = player.getWorld();
            if (world != null) {
                Player p = player;
                PlayerRef pr = playerRefComponent;
                CompletableFuture.runAsync(() -> {
                    mhudHide(p, pr, BLOOD_GAUGE_HUD_KEY);
                    mhudHide(p, pr, RELIC_COOLDOWN_HUD_KEY);
                }, (Executor) world);
            }
        }
        playerStates.remove(playerRef);
    }

    private void syncInputBindingsHud(@Nonnull Player player,
                                      PlayerRef playerRefComponent,
                                      @Nonnull SatietyState state,
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
        SatietyState state = playerStates.get(playerRef);
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
                          @Nonnull SatietyState state) {
        if (!MULTIPLE_HUD_AVAILABLE || playerRefComponent == null) return;
        World world = player.getWorld();
        if (world == null) return;

        Ref<EntityStore> pRef = playerRef;
        Player p = player;
        PlayerRef pr = playerRefComponent;

        CompletableFuture.runAsync(() -> {
            boolean bloodGaugeReady = bloodGaugeHuds.containsKey(pRef);
            if (!bloodGaugeReady) {
                BloodGaugeHud hud = new BloodGaugeHud(pr);
                bloodGaugeReady = mhudSet(p, pr, BLOOD_GAUGE_HUD_KEY, hud);
                if (bloodGaugeReady) {
                    bloodGaugeHuds.put(pRef, hud);
                    hud.sync(state.blood, state.maxBlood, p.getGameMode() == GameMode.Creative);
                }
            }

            boolean cooldownReady = relicCooldownHuds.containsKey(pRef);
            if (!cooldownReady) {
                RelicCooldownHud relicHud = new RelicCooldownHud(pr);
                cooldownReady = mhudSet(p, pr, RELIC_COOLDOWN_HUD_KEY, relicHud);
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

    private static int resolveBloodCapacityUnits(@Nonnull Ref<EntityStore> playerRef,
                                                 @Nonnull Store<EntityStore> store) {
        PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComponent == null) {
            return BASE_BLOOD_CAPACITY_UNITS;
        }
        ModifierContext ctx = new ModifierContext(playerRefComponent.getUuid(), playerRef, store);
        float multiplier = ModifierRegistry.get().compute(VampireStatType.BLOOD_BAR_CAPACITY, 1f, ctx);
        if (!Float.isFinite(multiplier) || multiplier <= 0f) {
            multiplier = 1f;
        }
        return Math.max(1, Math.round(BASE_BLOOD_CAPACITY_UNITS * multiplier));
    }

    private void applyVampireThroneRecovery(@Nonnull Ref<EntityStore> playerRef,
                                            @Nonnull Player player,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull SatietyState state,
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

    // --- MultipleHUD reflection helpers (avoids compile-time dependency) ---

    private static boolean mhudSet(Player player, PlayerRef pr, String key, CustomUIHud hud) {
        try {
            Class<?> cls = Class.forName("com.buuz135.mhud.MultipleHUD");
            Object mhud = cls.getMethod("getInstance").invoke(null);
            Method m = cls.getMethod("setCustomHud",
                    Player.class, PlayerRef.class, String.class, CustomUIHud.class);
            m.invoke(mhud, player, pr, key, hud);
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("[Satiety] mhudSet failed: " + e.getMessage());
            return false;
        }
    }

    private static void mhudHide(Player player, PlayerRef pr, String key) {
        try {
            Class<?> cls = Class.forName("com.buuz135.mhud.MultipleHUD");
            Object mhud = cls.getMethod("getInstance").invoke(null);
            Method m = cls.getMethod("hideCustomHud",
                    Player.class, PlayerRef.class, String.class);
            m.invoke(mhud, player, pr, key);
        } catch (Exception e) {
            LOGGER.atWarning().log("[Satiety] mhudHide failed: " + e.getMessage());
        }
    }

    private static class SatietyState {
        volatile int blood = BASE_BLOOD_CAPACITY_UNITS;
        volatile int maxBlood = BASE_BLOOD_CAPACITY_UNITS;
        volatile boolean isStarving = false;
        volatile long lastUpdateTime = System.currentTimeMillis();
        volatile long lastCooldownHudUpdateTime = 0L;
        volatile long firstSeenTime = System.currentTimeMillis();
        volatile boolean hudInitFailed = false;
        volatile boolean inputBindingsHidden = false;
        volatile boolean relicInventoryFullNotified = false;
        volatile float vampireThroneRecoveryAccumulator = 0f;
    }

    /** Context key for "is the player starving" — cached per compute() call. */
    public static final ContextKey<Boolean> IS_STARVING = new ContextKey<>() {};

    /** Context key for "player is overfed" (blood == max). */
    public static final ContextKey<Boolean> IS_OVERFED = new ContextKey<>() {};

    /** Context key for "blood state is normal" (above starving threshold but below overfed). */
    public static final ContextKey<Boolean> IS_BLOOD_STATE_NORMAL = new ContextKey<>() {};

    public static boolean isOverfed(@Nonnull Ref<EntityStore> playerRef) {
        return getBlood(playerRef) >= getMaxBlood(playerRef);
    }

    public static boolean isBloodStateNormal(@Nonnull Ref<EntityStore> playerRef) {
        int blood = getBlood(playerRef);
        return blood > VampirismConfig.get().getSatietyStarvingThreshold()
                && blood < getMaxBlood(playerRef);
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
