package com.epicseed.vampirism.systems;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.hunt.NightHuntService;
import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.epiccore.modifier.ModifierTag;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.registry.VampireStatusRegistry;
import com.epicseed.vampirism.skill.runtime.PassiveService;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeStateResolver;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageEventSystem;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageModule;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerInput;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class VampireCombatSystem extends DamageEventSystem {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, Boolean> ambushConsumed = new ConcurrentHashMap<>();
    private static final String[] COFFIN_BLOCK_IDS = {
            "Furniture_Ancient_Coffin",
            "Furniture_Human_Ruins_Coffin",
            "Furniture_Temple_Dark_Coffin",
            "Furniture_Village_Coffin"
    };
    private final PassiveService passiveService;

    public VampireCombatSystem(@Nonnull PassiveService passiveService) {
        this.passiveService = Objects.requireNonNull(passiveService, "passiveService");
    }

    @Override
    public SystemGroup<EntityStore> getGroup() {
        return DamageModule.get().getFilterDamageGroup();
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
            @Nonnull Damage damage) {
        try {
            @SuppressWarnings("unchecked")
            Ref<EntityStore> victimRef = (Ref<EntityStore>) chunk.getReferenceTo(index);

            // Resolve victim identity once; reused for victim-side modifiers and kill detection
            PlayerRef victimPlayerRef = (PlayerRef) store.getComponent(victimRef, PlayerRef.getComponentType());
            UUID victimUuid = victimPlayerRef != null ? victimPlayerRef.getUuid() : null;
            EntityStatMap victimStats = (EntityStatMap) store.getComponent(victimRef, EntityStatMap.getComponentType());
            EntityStatValue victimHealth = victimStats != null ? victimStats.get(DefaultEntityStatTypes.getHealth()) : null;
            float victimHealthBeforeHit = victimHealth != null ? Math.max(0f, victimHealth.get()) : -1f;

            if (damage.getCause() == DamageCause.SUFFOCATION && isMountedInCoffin(victimRef, store)) {
                damage.setAmount(0f);
                return;
            }

            // Fall damage reduction (victim-side) — uses FALL_DAMAGE_REDUCTION stat
            if (damage.getCause() == DamageCause.FALL) {
                handleFallDamage(damage, index, chunk, victimRef, victimUuid, store);
                return;
            }

            // DAMAGE_IN_REDUCTION for vampire victims (any attacker type)
            boolean vampireVictim = victimUuid != null && VampireStatusRegistry.get().isVampire(victimUuid);
            if (vampireVictim) {
                ModifierContext victimCtx = new ModifierContext(victimUuid, victimRef, store);
                float reduction = ModifierContext.REGISTRY.compute(VampireStatType.DAMAGE_IN_REDUCTION, 0f, victimCtx);
                if (reduction > 0f) {
                    damage.setAmount(Math.max(0f, damage.getAmount() * (1f - reduction)));
                }
            }

            Damage.Source source = damage.getSource();
            if (!(source instanceof Damage.EntitySource)) {
                dispatchDamageTaken(vampireVictim, victimUuid, victimRef, store, damage.getAmount());
                return;
            }

            @SuppressWarnings("unchecked")
            Ref<EntityStore> attackerRef = (Ref<EntityStore>) ((Damage.EntitySource) source).getRef();

            Player attackerPlayer = (Player) store.getComponent(attackerRef, Player.getComponentType());
            if (attackerPlayer == null) {
                dispatchDamageTaken(vampireVictim, victimUuid, victimRef, store, damage.getAmount());
                return;
            }

            PlayerRef attackerPlayerRef = (PlayerRef) store.getComponent(attackerRef, PlayerRef.getComponentType());
            UUID attackerUuid = attackerPlayerRef != null ? attackerPlayerRef.getUuid() : null;

            if (attackerUuid != null && !VampireStatusRegistry.get().isVampire(attackerUuid)) {
                ambushConsumed.remove(attackerUuid);
                dispatchDamageTaken(vampireVictim, victimUuid, victimRef, store, damage.getAmount());
                return;
            }

            ModifierContext ctx = new ModifierContext(attackerUuid, attackerRef, store);
            var reg = ModifierContext.REGISTRY;

            float damageMultiplier = reg.compute(VampireStatType.DAMAGE_OUT, 1.0f, ctx);
            float ambushBonus = reg.compute(VampireStatType.AMBUSH_DAMAGE_MULTIPLIER, 0.0f, ctx);
            boolean ambushEligible = isAmbushEligible(ctx);
            if (!ambushEligible && attackerUuid != null) {
                ambushConsumed.remove(attackerUuid);
            }
            boolean firstAmbushHit = ambushEligible && consumeAmbush(attackerUuid);
            if (ambushBonus > 0f && firstAmbushHit) {
                damageMultiplier *= (1f + ambushBonus);
            }
            damage.setAmount(Math.max(0f, damage.getAmount() * damageMultiplier));
            float resolvedDamageAmount = damage.getAmount();
            float effectiveDamageAmount = victimHealthBeforeHit > 0f
                    ? Math.min(resolvedDamageAmount, victimHealthBeforeHit)
                    : resolvedDamageAmount;

            dispatchDamageTaken(vampireVictim, victimUuid, victimRef, store, effectiveDamageAmount);

            // Dispatch onDamageDealt for vampire attackers so reactive passives can fire
            if (attackerUuid != null) {
                if (effectiveDamageAmount > 0f) {
                    NightHuntService.recordMarkedPreyHit(attackerUuid, victimRef, store);
                }
                passiveService.onDamageDealt(
                        new SkillRuntimeContext(attackerUuid, attackerRef, store),
                        effectiveDamageAmount);
                if (firstAmbushHit) {
                    passiveService.onFirstHit(new SkillRuntimeContext(attackerUuid, attackerRef, victimRef, store));
                }
            }

            // Fetch attacker stats once for all per-hit heals
            EntityStatMap attackerStats = (EntityStatMap) store.getComponent(attackerRef, EntityStatMap.getComponentType());

            // Lifesteal — fractional heal based on dealt damage
            float lifesteal = reg.compute(VampireStatType.LIFESTEAL, 0.0f, ctx);
            if (lifesteal > 0f && effectiveDamageAmount > 0f && attackerStats != null) {
                attackerStats.addStatValue(DefaultEntityStatTypes.getHealth(), effectiveDamageAmount * lifesteal);
            }

            // ON_HIT_HEAL — flat HP restored per hit (e.g. Blood Leach passive)
            float onHitHeal = reg.compute(VampireStatType.ON_HIT_HEAL, 0.0f, ctx);
            if (onHitHeal > 0f && attackerStats != null) {
                attackerStats.addStatValue(DefaultEntityStatTypes.getHealth(), onHitHeal);
            }

            // Blood Thirst/Frenzy stamina recovery
            float staminaRecovery = reg.compute(VampireStatType.FRENZY_STAMINA_RECOVERY, 0.0f, ctx);
            if (staminaRecovery > 0f && attackerStats != null) {
                int staminaIndex = DefaultEntityStatTypes.getStamina();
                EntityStatValue staminaStat = attackerStats.get(staminaIndex);
                if (staminaStat != null) {
                    attackerStats.addStatValue(staminaIndex, staminaStat.getMax() * staminaRecovery);
                }
            }

            // Kill detection for satiety gain, infection, and onKill passive trigger
            if (victimStats != null) {
                boolean lethalHit = victimHealthBeforeHit > 0f
                        ? effectiveDamageAmount >= victimHealthBeforeHit
                        : victimHealth != null && victimHealth.get() <= resolvedDamageAmount;
                if (lethalHit) {
                    String victimName = victimPlayerRef != null ? victimPlayerRef.getUsername() : "Unknown";
                    if (victimUuid != null) {
                        VampireVitalitySystem.onPlayerKill(attackerRef, store, victimRef, victimUuid, victimName);
                    }
                    if (attackerUuid != null) {
                        passiveService.onKill(
                                new SkillRuntimeContext(attackerUuid, attackerRef, victimRef, store));
                        NightHuntService.onPlayerKilledMarkedPrey(attackerUuid, attackerRef, victimRef, store);
                    }
                }
            }

        } catch (Exception e) {
            LOGGER.atSevere().log("[VampireCombatSystem] Error: " + e.getMessage());
        }
    }

    private void handleFallDamage(@Nonnull Damage damage, int index,
            @Nonnull ArchetypeChunk<EntityStore> chunk,
            @Nonnull Ref<EntityStore> victimRef,
            @Nullable UUID victimUuid,
            @Nonnull Store<EntityStore> store) {
        PlayerInput playerInput = (PlayerInput) chunk.getComponent(index, PlayerInput.getComponentType());
        if (playerInput == null)
            return;
        List queue = playerInput.getMovementUpdateQueue();
        for (int i = 0; i < queue.size(); i++) {
            PlayerInput.InputUpdate entry = (PlayerInput.InputUpdate) queue.get(i);
            if (entry instanceof PlayerInput.SetMovementStates move && move.movementStates().rolling) {
                ModifierContext ctx = new ModifierContext(victimUuid, victimRef, store);
                float reduction = ModifierContext.REGISTRY.compute(VampireStatType.FALL_DAMAGE_REDUCTION, 0f, ctx);
                float reduced = damage.getAmount() * (1f - reduction);
                LOGGER.atInfo().log("[Vampire] Roll fall damage: " + damage.getAmount() + " -> " + reduced);
                damage.setAmount(reduced);
                return;
            }
        }
    }

    private void dispatchDamageTaken(boolean vampireVictim,
            @Nullable UUID victimUuid,
            @Nonnull Ref<EntityStore> victimRef,
            @Nonnull Store<EntityStore> store,
            float finalDamageAmount) {
        if (!vampireVictim) {
            return;
        }
        passiveService.onDamageTaken(
                new SkillRuntimeContext(victimUuid, victimRef, store),
                finalDamageAmount);
    }

    private boolean isAmbushEligible(@Nonnull ModifierContext ctx) {
        return SkillRuntimeStateResolver.isStateActive("IS_INVISIBLE", ctx)
                || SkillRuntimeStateResolver.isStateActive("IS_SNEAKING", ctx);
    }

    private boolean consumeAmbush(@Nullable UUID uuid) {
        if (uuid == null) return false;
        return ambushConsumed.putIfAbsent(uuid, Boolean.TRUE) == null;
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        if (uuid != null) {
            ambushConsumed.remove(uuid);
        }
    }

    private boolean isMountedInCoffin(@Nonnull Ref<EntityStore> victimRef,
                                      @Nonnull Store<EntityStore> store) {
        MountedComponent mounted = store.getComponent(victimRef, MountedComponent.getComponentType());
        if (mounted == null || mounted.getMountedToBlock() == null || !mounted.getMountedToBlock().isValid()) {
            return false;
        }

        World world = currentWorld(store);
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

        String blockId = blockMount.getExpectedBlockType().getId();
        if (blockId == null) {
            return false;
        }
        for (String coffinBlockId : COFFIN_BLOCK_IDS) {
            if (coffinBlockId.equals(blockId)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private World currentWorld(@Nonnull Store<EntityStore> store) {
        Object externalData = store.getExternalData();
        if (!(externalData instanceof EntityStore entityStore)) {
            return null;
        }
        return entityStore.getWorld();
    }

    /** Tags for modifiers registered by this system. */
    public enum Tag implements ModifierTag {
        ROLLING_FALL_WARD;
        @Override public String key() { return "combat:" + name(); }
    }

    /** Registers global modifiers owned by this system. Call once at plugin startup. */
    public static void registerModifiers() {
        ModifierContext.REGISTRY.registerGlobal(VampireStatType.FALL_DAMAGE_REDUCTION, Tag.ROLLING_FALL_WARD, 10,
                (current, ctx) -> current + VampirismConfig.get().getFallDamageReduction());
    }
}
