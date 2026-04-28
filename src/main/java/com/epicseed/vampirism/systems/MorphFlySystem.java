package com.epicseed.vampirism.systems;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.protocol.SavedMovementStates;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.ActiveEntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Enables flying for players who have the Potion_Morph_Bat effect active,
 * and revokes it when the effect is removed.
 * Runs every tick but only sends movement update packets on state transitions.
 */
public class MorphFlySystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String EFFECT_ID = "Potion_Morph_Bat";
    private static final float MIN_RESTORE_DURATION = 0.1f;

    private static volatile EntityEffect cachedEffect = null;
    private static volatile int cachedEffectIndex = Integer.MIN_VALUE;

    // UUIDs for which this system granted canFly (used to avoid revoking fly from other sources)
    private static final Set<UUID> morphFlyGranted = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, PendingMorphRestore> liveMorphSnapshots = new ConcurrentHashMap<>();
    private static final java.util.Map<UUID, PendingMorphRestore> pendingMorphRestores = new ConcurrentHashMap<>();

    private record PendingMorphRestore(boolean infinite, long expiresAtMs) {}

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

            if (store.getComponent(playerRef, Player.getComponentType()) == null) return;

            PlayerRef playerRefComp = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            if (playerRefComp == null) return;

            int effectIndex = getEffectIndex();
            if (effectIndex < 0) return;

            EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                    playerRef, EffectControllerComponent.getComponentType());
            if (ec == null) return;

            UUID uuid = playerRefComp.getUuid();

            MovementManager mm = (MovementManager) store.getComponent(
                    playerRef, MovementManager.getComponentType());
            if (mm == null) return;

            boolean hasMorph = ec.hasEffect(effectIndex);
            MovementSettings settings = mm.getSettings();
            MovementStatesComponent statesComponent = (MovementStatesComponent) store.getComponent(
                    playerRef, MovementStatesComponent.getComponentType());

            if (hasMorph) {
                updateLiveSnapshot(uuid, ec, effectIndex);
                boolean firstMorphTick = morphFlyGranted.add(uuid);
                boolean settingsChanged = !settings.canFly;
                if (!settings.canFly) {
                    settings.canFly = true;
                }
                if (firstMorphTick || settingsChanged) {
                    mm.update(playerRefComp.getPacketHandler());
                }
            } else if (morphFlyGranted.remove(uuid)) {
                liveMorphSnapshots.remove(uuid);
                forceStopFlying(playerRef, store, statesComponent);
                if (settings.canFly) {
                    settings.canFly = false;
                }
                mm.update(playerRefComp.getPacketHandler());
            } else {
                liveMorphSnapshots.remove(uuid);
            }
        } catch (Exception e) {
            LOGGER.atSevere().log("[MorphFlySystem] Error: " + e.getMessage());
        }
    }

    private static int getEffectIndex() {
        if (cachedEffectIndex == Integer.MIN_VALUE) {
            int idx = EntityEffect.getAssetMap().getIndex(EFFECT_ID);
            if (idx < 0) return -1;
            cachedEffectIndex = idx;
            cachedEffect = EntityEffect.getAssetMap().getAsset(idx);
            LOGGER.atInfo().log("[MorphFlySystem] EntityEffect '" + EFFECT_ID + "' loaded at index " + idx);
        }
        return cachedEffectIndex;
    }

    public static void clearState(UUID uuid) {
        morphFlyGranted.remove(uuid);
        liveMorphSnapshots.remove(uuid);
        pendingMorphRestores.remove(uuid);
    }

    public static void clearTransientState(@Nonnull UUID uuid) {
        morphFlyGranted.remove(uuid);
        liveMorphSnapshots.remove(uuid);
    }

    public static void captureDisconnectState(@Nonnull UUID uuid) {
        morphFlyGranted.remove(uuid);
        PendingMorphRestore snapshot = liveMorphSnapshots.get(uuid);
        if (snapshot == null || (!snapshot.infinite() && snapshot.expiresAtMs() <= System.currentTimeMillis())) {
            pendingMorphRestores.remove(uuid);
            return;
        }
        pendingMorphRestores.put(uuid, snapshot);
    }

    public static void onPlayerReady(@Nonnull Ref<EntityStore> playerRef) {
        if (!playerRef.isValid()) {
            return;
        }

        @SuppressWarnings("unchecked")
        Store<EntityStore> store = (Store<EntityStore>) playerRef.getStore();
        PlayerRef playerRefComp = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
        if (playerRefComp == null) {
            return;
        }

        int effectIndex = getEffectIndex();
        if (effectIndex < 0) {
            return;
        }

        EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                playerRef, EffectControllerComponent.getComponentType());
        if (ec == null) {
            return;
        }

        restorePendingMorph(playerRef, ec, store, playerRefComp.getUuid(), effectIndex);
        if (!ec.hasEffect(effectIndex)) {
            return;
        }

        updateLiveSnapshot(playerRefComp.getUuid(), ec, effectIndex);
        ensureMorphModel(playerRef, ec, store, effectIndex);

        MovementManager mm = (MovementManager) store.getComponent(playerRef, MovementManager.getComponentType());
        if (mm == null) {
            return;
        }

        MovementSettings settings = mm.getSettings();
        if (!settings.canFly) {
            settings.canFly = true;
            mm.update(playerRefComp.getPacketHandler());
        }
        morphFlyGranted.add(playerRefComp.getUuid());
    }

    public static boolean isMorphActiveByUuid(@Nonnull UUID uuid) {
        return morphFlyGranted.contains(uuid) || pendingMorphRestores.containsKey(uuid);
    }

    public static boolean isMorphActive(@Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull Store<EntityStore> store,
                                        @Nonnull UUID uuid) {
        int effectIndex = getEffectIndex();
        if (effectIndex >= 0) {
            EffectControllerComponent ec = (EffectControllerComponent) store.getComponent(
                    playerRef, EffectControllerComponent.getComponentType());
            if (ec != null && ec.hasEffect(effectIndex)) {
                return true;
            }
        }
        return morphFlyGranted.contains(uuid) || pendingMorphRestores.containsKey(uuid);
    }

    private static void forceStopFlying(@Nonnull Ref<EntityStore> playerRef,
                                        @Nonnull Store<EntityStore> store,
                                        MovementStatesComponent statesComponent) {
        if (statesComponent == null) return;
        MovementStates states = statesComponent.getMovementStates();
        MovementStates updated = null;
        if (states != null) {
            updated = new MovementStates(states);
            updated.flying = false;
            updated.gliding = false;
            updated.swimming = false;
            updated.swimJumping = false;
            updated.mantling = false;
            updated.sliding = false;
            updated.jumping = false;
            if (!updated.onGround) {
                updated.falling = true;
                updated.onGround = false;
            }
            statesComponent.setMovementStates(updated);
        }
        MovementStates sentStates = statesComponent.getSentMovementStates();
        if (sentStates != null) {
            MovementStates updatedSent = updated != null ? new MovementStates(updated) : new MovementStates(sentStates);
            updatedSent.flying = false;
            updatedSent.gliding = false;
            updatedSent.swimming = false;
            updatedSent.swimJumping = false;
            updatedSent.mantling = false;
            updatedSent.sliding = false;
            updatedSent.jumping = false;
            if (!updatedSent.onGround) {
                updatedSent.falling = true;
                updatedSent.onGround = false;
            }
            statesComponent.setSentMovementStates(updatedSent);
            updated = updatedSent;
        }
        if (updated != null) {
            Player player = (Player) store.getComponent(playerRef, Player.getComponentType());
            if (player != null) {
                player.applyMovementStates(playerRef, new SavedMovementStates(false), updated, store);
                Velocity velocity = (Velocity) store.getComponent(playerRef, Velocity.getComponentType());
                if (velocity != null) {
                    player.resetVelocity(velocity);
                }
            }
        }
    }

    private static void restorePendingMorph(@Nonnull Ref<EntityStore> playerRef,
                                            @Nonnull EffectControllerComponent ec,
                                            @Nonnull Store<EntityStore> store,
                                            @Nonnull UUID uuid,
                                            int effectIndex) {
        PendingMorphRestore pending = pendingMorphRestores.get(uuid);
        if (pending == null) {
            return;
        }
        if (ec.hasEffect(effectIndex)) {
            pendingMorphRestores.remove(uuid);
            updateLiveSnapshot(uuid, ec, effectIndex);
            return;
        }

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
        if (effect == null) {
            LOGGER.atWarning().log("[MorphFlySystem] Failed to restore bat morph for " + uuid + ": effect asset missing");
            pendingMorphRestores.remove(uuid);
            return;
        }

        if (pending.infinite()) {
            ec.addInfiniteEffect(playerRef, effectIndex, effect, store);
        } else {
            float remainingDuration = (pending.expiresAtMs() - System.currentTimeMillis()) / 1000f;
            if (remainingDuration <= 0f) {
                pendingMorphRestores.remove(uuid);
                return;
            }
            ec.addEffect(playerRef, effectIndex, effect,
                    Math.max(MIN_RESTORE_DURATION, remainingDuration),
                    OverlapBehavior.OVERWRITE, store);
        }
        pendingMorphRestores.remove(uuid);
    }

    private static void updateLiveSnapshot(@Nonnull UUID uuid,
                                           @Nonnull EffectControllerComponent ec,
                                           int effectIndex) {
        ActiveEntityEffect active = ec.getActiveEffects().get(effectIndex);
        if (active == null) {
            liveMorphSnapshots.remove(uuid);
            return;
        }
        long expiresAtMs = active.isInfinite()
                ? Long.MAX_VALUE
                : System.currentTimeMillis()
                        + (long) Math.ceil(Math.max(0f, active.getRemainingDuration()) * 1000f);
        liveMorphSnapshots.put(uuid, new PendingMorphRestore(active.isInfinite(), expiresAtMs));
    }

    private static void ensureMorphModel(@Nonnull Ref<EntityStore> playerRef,
                                         @Nonnull EffectControllerComponent ec,
                                         @Nonnull Store<EntityStore> store,
                                         int effectIndex) {
        EntityEffect effect = cachedEffect;
        if (effect == null || effectIndex != cachedEffectIndex) {
            effect = EntityEffect.getAssetMap().getAsset(effectIndex);
            cachedEffect = effect;
        }
        if (effect == null) {
            LOGGER.atWarning().log("[MorphFlySystem] Failed to restore bat morph model: effect asset missing");
            return;
        }
        ec.setModelChange(playerRef, effect, effectIndex, store);
    }

    /** Context key for "is the player in bat form" — cached per compute() call. */
    public static final com.epicseed.vampirism.modifier.ContextKey<Boolean> IS_IN_BAT_FORM =
            new com.epicseed.vampirism.modifier.ContextKey<>() {};
}
