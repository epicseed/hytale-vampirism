package com.epicseed.vampirism.systems;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.epiccore.modifier.ContextKey;
import com.epicseed.vampirism.interop.VampirismClassifications;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.epiccore.modifier.ModifierTag;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.runtime.TemporaryModifierTracker;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.SystemGroup;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class VampireMovementSystem extends EntityTickingSystem<EntityStore> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final Map<UUID, Float> updateAccumulators = new ConcurrentHashMap<>();

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

            PlayerRef playerRefComponent = (PlayerRef) store.getComponent(playerRef, PlayerRef.getComponentType());
            MovementManager movementManager = (MovementManager) store.getComponent(playerRef, MovementManager.getComponentType());
            if (movementManager == null) return;

            UUID uuid = playerRefComponent != null ? playerRefComponent.getUuid() : null;
            if (uuid != null) {
                float intervalSeconds = Math.max(1, VampirismConfig.get().getSpeedTicksBetweenUpdates()) / 20f;
                float accumulated = updateAccumulators.getOrDefault(uuid, 0f) + dt;
                if (accumulated < intervalSeconds) {
                    updateAccumulators.put(uuid, accumulated);
                    return;
                }
                updateAccumulators.put(uuid, 0f);
            }

            // Restore normal speed for non-vampires
            if (playerRefComponent != null && !VampirismClassifications.isVampiric(playerRefComponent.getUuid())) {
                clearPlayer(playerRefComponent.getUuid());
                applySpeed(movementManager, playerRefComponent, com.epicseed.vampirism.config.VampirismConfig.get().getSpeedNormal());
                return;
            }

            ModifierContext ctx = new ModifierContext(uuid, playerRef, store);
            float targetSpeed = ModifierContext.REGISTRY.compute(VampireStatType.SPEED, 0f, ctx)
                    + ModifierContext.REGISTRY.compute(VampireStatType.BAT_FORM_SPEED, 0f, ctx)
                    + ModifierContext.REGISTRY.compute(VampireStatType.ANCIENT_FORM_SPEED, 0f, ctx);

            applySpeed(movementManager, playerRefComponent, targetSpeed);

        } catch (Exception e) {
            LOGGER.atSevere().log("[VampireMovementSystem] Error: " + e.getMessage());
        }
    }

    public static void clearPlayer(UUID uuid) {
        if (uuid != null) {
            updateAccumulators.remove(uuid);
        }
    }

    private void applySpeed(@Nonnull MovementManager movementManager,
                            PlayerRef playerRefComponent,
                            float targetSpeed) {
        MovementSettings settings = movementManager.getSettings();
        if (Math.abs(settings.baseSpeed - targetSpeed) > 0.01f) {
            settings.baseSpeed = targetSpeed;
            if (playerRefComponent != null) {
                movementManager.update(playerRefComponent.getPacketHandler());
            }
        }
    }

    /** Tags for modifiers registered by this system. */
    public enum Tag implements ModifierTag {
        WORLD_STATE_SPEED, PREDATOR_SURGE;
        @Override public String key() { return "movement:" + name(); }
    }

    /** Registers global modifiers owned by this system. Call once at plugin startup. */
    public static void registerModifiers() {
        var reg = ModifierContext.REGISTRY;
        reg.registerGlobal(VampireStatType.SPEED, Tag.WORLD_STATE_SPEED, 10, (current, ctx) -> {
            boolean inSunlight = ctx.resolve(SunburnSystem.IN_SUNLIGHT, () -> SunburnSystem.isInSunlight(ctx.uuid()));
            return inSunlight ? VampirismConfig.get().getSpeedDay() : VampirismConfig.get().getSpeedNight();
        });
        // Boiling Blood: timed speed boost registered by TemporaryModifierTracker on kill
        reg.registerGlobal(VampireStatType.SPEED, Tag.PREDATOR_SURGE, 150, (current, ctx) -> {
            float boost = TemporaryModifierTracker.sumAdditive(ctx.uuid(), VampireStatType.SPEED);
            return boost > 0f ? current + boost : current;
        });
    }
}
