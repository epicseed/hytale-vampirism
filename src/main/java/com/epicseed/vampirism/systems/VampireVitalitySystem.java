package com.epicseed.vampirism.systems;
import com.epicseed.vampirism.modifier.ModifierContext;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.hytale.runtime.PlayerRuntimeIndex;
import com.epicseed.vampirism.domain.blood.BloodHudService;
import com.epicseed.vampirism.domain.blood.BloodService;
import com.epicseed.vampirism.domain.blood.BloodState;
import com.epicseed.vampirism.domain.blood.RelicOwnershipSyncService;
import com.epicseed.vampirism.domain.blood.VampireThroneRecoveryService;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.ui.RelicBindingsUI;
import com.epicseed.vampirism.ui.SkillTreeUI;
import com.epicseed.epiccore.modifier.ContextKey;
import com.epicseed.epiccore.modifier.ModifierTag;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.relic.RelicInventoryService;
import com.epicseed.vampirism.relic.RelicPresetProjectionService;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class VampireVitalitySystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public static final int BASE_BLOOD_CAPACITY_UNITS = BloodService.BASE_BLOOD_CAPACITY_UNITS;

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

        BloodHudService.syncBlood(attackerRef, state);
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
        BloodHudService.syncBlood(playerRef, state);
    }

    public static int addBlood(@Nonnull Ref<EntityStore> playerRef, int bloodGain) {
        if (bloodGain <= 0) {
            return getBlood(playerRef);
        }
        BloodState state = BloodService.addBlood(playerRef, bloodGain);
        BloodHudService.syncBlood(playerRef, state);
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
        return BloodHudService.isHudActiveByUuid(uuid);
    }

    public static void captureDisconnectState(@Nonnull UUID uuid) {
        BloodService.captureDisconnectState(uuid);
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        BloodService.clearPlayer(uuid);
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
                RelicBindingsUI.refreshOpenState(playerRefComponent.getUuid());
            }
            if (playerRefComponent == null) {
                return;
            }
            boolean creativeMode = player.getGameMode() == GameMode.Creative;
            boolean isVampire = VampireStatusRegistry.get().isVampire(playerRefComponent.getUuid());
            boolean relicInHand = isRelicInHand(playerRef, store);

            if (!isVampire) {
                RelicPresetProjectionService.sync(playerRefComponent.getUuid(), playerRef, store, false);
                RelicOwnershipSyncService.syncNonVampire(playerRef, store);
                cleanupPlayer(playerRef, player, playerRefComponent);
                return;
            }

            BloodService.registerRef(playerRefComponent.getUuid(), playerRef);
            PlayerRuntimeIndex.register(playerRefComponent.getUuid(), playerRef);

            BloodState state = BloodService.getOrCreateLoaded(playerRef, playerRefComponent.getUuid(), store);

            RelicOwnershipSyncService.syncVampire(playerRef, store, player, state);
            RelicPresetProjectionService.sync(playerRefComponent.getUuid(), playerRef, store, relicInHand);

            BloodService.refreshCapacity(state, playerRef, store);

            long now = System.currentTimeMillis();

            // HUD initialization
            BloodHudService.tryInitialize(playerRef, player, playerRefComponent, store, state, now);

            BloodHudService.syncInputBindings(player, playerRefComponent, state, relicInHand);
            BloodHudService.refreshRelicCooldowns(playerRef, store, state, now, blockRelicHudRefresh, skillTreeOpen);
            BloodHudService.syncCreativeMode(playerRef, creativeMode);

            if (now - state.lastUpdateTime < VampirismConfig.get().getSatietyUpdateIntervalMs()) return;

            float elapsedSeconds = (float)(now - state.lastUpdateTime) / 1000f;
            state.lastUpdateTime = now;

            VampireThroneRecoveryService.apply(playerRef, player, store, state, elapsedSeconds);

            // Starvation check
            if (!state.isStarving && state.blood <= VampirismConfig.get().getSatietyStarvingThreshold()) {
                state.isStarving = true;
                LOGGER.atInfo().log("[Satiety] Player " + playerRef + " is STARVING! (" + state.blood + "/" + state.maxBlood + ")");
            } else if (state.isStarving && state.blood >= VampirismConfig.get().getSatietyRecoveryThreshold()) {
                state.isStarving = false;
                LOGGER.atInfo().log("[Satiety] Player " + playerRef + " recovered from starvation.");
            }

            BloodHudService.syncBloodGauge(playerRef, state, creativeMode);

        } catch (Exception e) {
            LOGGER.atSevere().log("[VampireVitalitySystem] Error: " + e.getMessage());
        }
    }

    private void cleanupPlayer(@Nonnull Ref<EntityStore> playerRef,
                               @Nonnull Player player,
                               PlayerRef playerRefComponent) {
        if (playerRefComponent != null) {
            PlayerRuntimeIndex.unregister(playerRefComponent.getUuid());
        }
        BloodHudService.cleanup(playerRef, player, playerRefComponent, stateInputBindingsHidden(playerRef));
        BloodService.removeState(playerRef);
    }

    private boolean stateInputBindingsHidden(@Nonnull Ref<EntityStore> playerRef) {
        BloodState state = BloodService.getState(playerRef);
        return state != null && state.inputBindingsHidden;
    }

    private boolean isRelicInHand(@Nonnull Ref<EntityStore> playerRef,
                                   @Nonnull Store<EntityStore> store) {
        ItemStack stack = InventoryComponent.getItemInHand(store, playerRef);
        return stack != null && RelicInventoryService.RELIC_ITEM_ID.equals(stack.getItemId());
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
        var reg = ModifierContext.REGISTRY;

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
