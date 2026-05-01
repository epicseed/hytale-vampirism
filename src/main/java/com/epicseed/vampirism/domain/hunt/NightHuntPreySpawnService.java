package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.registry.NightHuntSpawnRegistry;
import com.epicseed.vampirism.skill.runtime.PlayerRegistrySkillProgressionAccess;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.nameplate.Nameplate;
import com.hypixel.hytale.server.core.modules.entity.component.DisplayNameComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.Modifier.ModifierTarget;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier;
import com.hypixel.hytale.server.core.modules.entitystats.modifier.StaticModifier.CalculationType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.npc.INonPlayerCharacter;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import it.unimi.dsi.fastutil.Pair;

public final class NightHuntPreySpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String PREY_HEALTH_MODIFIER_KEY = "night_hunt_prey_health_bonus";

    private NightHuntPreySpawnService() {
    }

    public static boolean spawnMarkedPrey(@Nonnull UUID ownerUuid,
                                          @Nonnull Ref<EntityStore> playerRef,
                                          @Nonnull HuntState state,
                                          @Nonnull Store<EntityStore> store,
                                          int currentHour) {
        if (state.destination == null) {
            return false;
        }

        NightHuntCleanupService.clearGuideWisps(state, null);
        NightHuntCleanupService.removeEntityImmediately(state.waypointDisplayRef);
        state.waypointDisplayRef = null;
        state.waypointDisplayActive = false;

        NightHuntSpawnRegistry.SpawnOption spawnOption = NightHuntSpawnRegistry.get().pickSpawn(
                new NightHuntSpawnRegistry.SpawnContext(
                        PlayerRegistrySkillProgressionAccess.instance().getAcquiredSkillPoints(ownerUuid),
                        state.completedWaypoints,
                        state.forced,
                        currentHour,
                        state.visualTier));

        return spawnPrey(ownerUuid, playerRef, state, store, state.destination,
                spawnOption.roleId(), spawnOption.displayName(), spawnOption.dropPoints(),
                spawnOption.archetype(), Math.max(state.visualTier, spawnOption.visualTier()), spawnOption.elite(),
                spawnOption.healthMultiplier(), spawnOption.helperRoleId(), spawnOption.helperCount(),
                spawnOption.helperSpreadRadius(), spawnOption.onSpawnMessage(), spawnOption.preyLifetimeSeconds());
    }

    public static boolean spawnAmbushPrey(@Nonnull UUID ownerUuid,
                                          @Nonnull Ref<EntityStore> playerRef,
                                          @Nonnull TransformComponent playerTransform,
                                          @Nonnull HuntState state,
                                          @Nonnull NightHuntSpawnRegistry.FailStateOption failState,
                                          @Nonnull Store<EntityStore> store,
                                          @Nullable World world) {
        Vector3d spawnPosition = playerTransform.getPosition();
        if (world != null) {
            Vector3d candidate = NightHuntRouteService.findHuntDestination(
                    playerTransform.getPosition(),
                    playerTransform.getRotation() != null ? playerTransform.getRotation().getYaw() + 180.0f : 180.0f,
                    world,
                    4.0d,
                    8.0d);
            if (candidate != null) {
                spawnPosition = candidate;
            }
        }

        return spawnPrey(ownerUuid, playerRef, state, store, spawnPosition,
                failState.ambushRoleId(), failState.ambushDisplayName() != null ? failState.ambushDisplayName() : "Blood Ambusher",
                failState.ambushDropPoints(), failState.ambushArchetype(), failState.ambushVisualTier(),
                false, failState.ambushHealthMultiplier(), failState.ambushHelperRoleId(),
                failState.ambushHelperCount(), failState.ambushHelperSpreadRadius(),
                failState.text(), failState.ambushLifetimeSeconds());
    }

    private static boolean spawnPrey(@Nonnull UUID ownerUuid,
                                     @Nonnull Ref<EntityStore> playerRef,
                                     @Nonnull HuntState state,
                                     @Nonnull Store<EntityStore> store,
                                     @Nonnull Vector3d spawnPosition,
                                     @Nonnull String roleId,
                                     @Nonnull String displayName,
                                     int rewardPoints,
                                     @Nonnull String archetype,
                                     int visualTier,
                                     boolean elite,
                                     float healthMultiplier,
                                     @Nullable String helperRoleId,
                                     int helperCount,
                                     double helperSpreadRadius,
                                     @Nullable String onSpawnMessage,
                                     float preyLifetimeSeconds) {
        Pair<Ref<EntityStore>, INonPlayerCharacter> spawn =
                NPCPlugin.get().spawnNPC(store, roleId, null, spawnPosition, new Vector3f());
        if (spawn == null || spawn.first() == null) {
            LOGGER.atWarning().log("[NightHuntPreySpawnService] Failed to spawn marked prey for role " + roleId);
            return false;
        }

        Ref<EntityStore> preyRef = spawn.first();
        UUID preyUuid = extractEntityUuid(preyRef, store);
        if (preyUuid == null) {
            LOGGER.atWarning().log("[NightHuntPreySpawnService] Spawned prey without UUID");
            if (preyRef.isValid()) {
                store.removeEntity(preyRef, RemoveReason.REMOVE);
            }
            return false;
        }

        store.putComponent(preyRef, DisplayNameComponent.getComponentType(),
                new DisplayNameComponent(Message.raw(displayName).color(elite ? "red" : "dark_red")));
        Nameplate nameplate = (Nameplate) store.ensureAndGetComponent(preyRef, Nameplate.getComponentType());
        nameplate.setText(elite ? "[ELITE] " + displayName : displayName);
        applyHealthMultiplier(preyRef, healthMultiplier, store);

        state.phase = HuntPhase.PREY_ACTIVE;
        state.ownerUuid = ownerUuid;
        state.ownerPlayerRef = playerRef;
        state.preyRef = preyRef;
        state.preyEntityUuid = preyUuid;
        state.preyLifetimeRemainingSeconds = preyLifetimeSeconds > 0f
                ? preyLifetimeSeconds
                : VampirismConfig.get().getNightHuntPreyLifetimeSeconds();
        state.summonRemainingSeconds = 0f;
        state.preyRewardPoints = Math.max(0, rewardPoints);
        state.visualTier = NightHuntVisualService.clampVisualTier(visualTier);
        NightHuntTrackingService.trackPrey(preyUuid, ownerUuid);
        NightHuntCleanupService.clearHelperRefs(state, null);
        spawnHelpers(state, preyRef, helperRoleId, helperCount, helperSpreadRadius, store);
        NightHuntMessages.send(playerRef, store, onSpawnMessage != null ? onSpawnMessage : NightHuntMessages.SPAWN, "red");
        return true;
    }

    private static void spawnHelpers(@Nonnull HuntState state,
                                     @Nonnull Ref<EntityStore> preyRef,
                                     @Nullable String helperRoleId,
                                     int helperCount,
                                     double helperSpreadRadius,
                                     @Nonnull Store<EntityStore> store) {
        if (helperRoleId == null || helperCount <= 0) {
            return;
        }

        TransformComponent preyTransform = (TransformComponent) store.getComponent(preyRef, TransformComponent.getComponentType());
        if (preyTransform == null) {
            return;
        }

        Vector3d center = preyTransform.getPosition();
        for (int i = 0; i < helperCount; i++) {
            double angle = (Math.PI * 2.0d * i) / Math.max(1, helperCount);
            Vector3d spawnPosition = new Vector3d(
                    center.x + Math.cos(angle) * helperSpreadRadius,
                    center.y,
                    center.z + Math.sin(angle) * helperSpreadRadius);
            Pair<Ref<EntityStore>, INonPlayerCharacter> spawn =
                    NPCPlugin.get().spawnNPC(store, helperRoleId, null, spawnPosition, new Vector3f());
            if (spawn == null || spawn.first() == null) {
                continue;
            }
            state.helperRefs.add(spawn.first());
        }
    }

    private static void applyHealthMultiplier(@Nonnull Ref<EntityStore> preyRef,
                                              float healthMultiplier,
                                              @Nonnull Store<EntityStore> store) {
        EntityStatMap stats = (EntityStatMap) store.getComponent(preyRef, EntityStatMap.getComponentType());
        if (stats == null || healthMultiplier <= 1.0f) {
            return;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            return;
        }
        float desiredBonus = Math.max(0f, health.getMax() * (healthMultiplier - 1.0f));
        if (desiredBonus <= 0f) {
            return;
        }
        stats.putModifier(DefaultEntityStatTypes.getHealth(), PREY_HEALTH_MODIFIER_KEY,
                new StaticModifier(ModifierTarget.MAX, CalculationType.ADDITIVE, desiredBonus));
        stats.addStatValue(DefaultEntityStatTypes.getHealth(), desiredBonus);
        stats.update();
    }

    @Nullable
    private static UUID extractEntityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComponent = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }
}
