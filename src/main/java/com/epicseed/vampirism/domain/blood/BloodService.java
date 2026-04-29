package com.epicseed.vampirism.domain.blood;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class BloodService {
    public static final int BASE_BLOOD_CAPACITY_UNITS = 100;

    private static final Map<UUID, BloodState> playerStates = new ConcurrentHashMap<>();
    private static final Map<UUID, Ref<EntityStore>> uuidToRef = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, UUID> refToUuid = new ConcurrentHashMap<>();
    private static final Map<Ref<EntityStore>, BloodState> legacyRefStates = new ConcurrentHashMap<>();

    private BloodService() {
    }

    public static BloodState getOrCreate(@Nonnull Ref<EntityStore> playerRef) {
        UUID uuid = refToUuid.get(playerRef);
        if (uuid != null) {
            return playerStates.computeIfAbsent(uuid, ignored -> new BloodState());
        }
        return legacyRefStates.computeIfAbsent(playerRef, ignored -> new BloodState());
    }

    public static BloodState getOrCreateLoaded(@Nonnull Ref<EntityStore> playerRef,
                                               @Nonnull UUID uuid,
                                               @Nonnull Store<EntityStore> store) {
        registerRef(uuid, playerRef);
        return playerStates.computeIfAbsent(uuid, ignored -> {
            BloodState loaded = new BloodState();
            loaded.blood = PlayerSkillRegistry.get().getPersistedBlood(uuid);
            loaded.maxBlood = resolveCapacityUnits(playerRef, store);
            loaded.blood = clamp(loaded.blood, 0, loaded.maxBlood);
            loaded.isStarving = loaded.blood <= VampirismConfig.get().getSatietyStarvingThreshold();
            return loaded;
        });
    }

    public static BloodState getState(@Nonnull Ref<EntityStore> playerRef) {
        UUID uuid = refToUuid.get(playerRef);
        if (uuid != null) {
            return playerStates.get(uuid);
        }
        return legacyRefStates.get(playerRef);
    }

    public static void removeState(@Nonnull Ref<EntityStore> playerRef) {
        UUID uuid = refToUuid.remove(playerRef);
        if (uuid != null) {
            playerStates.remove(uuid);
            Ref<EntityStore> currentRef = uuidToRef.get(uuid);
            if (playerRef.equals(currentRef)) {
                uuidToRef.remove(uuid);
            }
        }
        legacyRefStates.remove(playerRef);
    }

    public static void registerRef(@Nonnull UUID uuid, @Nonnull Ref<EntityStore> playerRef) {
        uuidToRef.put(uuid, playerRef);
        refToUuid.put(playerRef, uuid);
        BloodState legacy = legacyRefStates.remove(playerRef);
        if (legacy != null) {
            playerStates.putIfAbsent(uuid, legacy);
        }
    }

    public static void unregisterRef(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = uuidToRef.remove(uuid);
        if (ref != null) {
            refToUuid.remove(ref);
        }
    }

    public static Ref<EntityStore> getRefByUuid(@Nonnull UUID uuid) {
        return uuidToRef.get(uuid);
    }

    public static boolean isStarving(@Nonnull Ref<EntityStore> playerRef) {
        BloodState state = playerStates.get(playerRef);
        return state != null && state.isStarving;
    }

    public static int getBlood(@Nonnull Ref<EntityStore> playerRef) {
        BloodState state = playerStates.get(playerRef);
        return state != null ? state.blood : BASE_BLOOD_CAPACITY_UNITS;
    }

    public static int getMaxBlood(@Nonnull Ref<EntityStore> playerRef) {
        BloodState state = playerStates.get(playerRef);
        return state != null ? Math.max(1, state.maxBlood) : BASE_BLOOD_CAPACITY_UNITS;
    }

    public static boolean canAffordBlood(@Nonnull Ref<EntityStore> playerRef, int bloodCost) {
        if (bloodCost <= 0) return true;
        return getBlood(playerRef) >= bloodCost;
    }

    public static BloodState spendBlood(@Nonnull Ref<EntityStore> playerRef, int bloodCost) {
        BloodState state = getOrCreate(playerRef);
        if (bloodCost > 0) {
            state.blood = Math.max(0, state.blood - bloodCost);
        }
        return state;
    }

    public static BloodState addBlood(@Nonnull Ref<EntityStore> playerRef, int bloodGain) {
        BloodState state = getOrCreate(playerRef);
        if (bloodGain <= 0) {
            return state;
        }
        state.blood = Math.min(state.maxBlood, state.blood + bloodGain);
        if (state.isStarving && state.blood >= VampirismConfig.get().getSatietyRecoveryThreshold()) {
            state.isStarving = false;
        }
        return state;
    }

    public static int getBloodByUuid(@Nonnull UUID uuid) {
        BloodState state = playerStates.get(uuid);
        return state != null ? state.blood : -1;
    }

    public static int getMaxBloodByUuid(@Nonnull UUID uuid) {
        BloodState state = playerStates.get(uuid);
        return state != null ? Math.max(1, state.maxBlood) : -1;
    }

    public static boolean isStarvingByUuid(@Nonnull UUID uuid) {
        BloodState state = playerStates.get(uuid);
        return state != null && state.isStarving;
    }

    public static void captureDisconnectState(@Nonnull UUID uuid) {
        BloodState state = playerStates.get(uuid);
        if (state == null) return;
        PlayerSkillRegistry.get().setPersistedBlood(uuid, state.blood);
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        Ref<EntityStore> ref = uuidToRef.remove(uuid);
        if (ref != null) {
            refToUuid.remove(ref);
            legacyRefStates.remove(ref);
        }
        playerStates.remove(uuid);
    }

    public static int resolveCapacityUnits(@Nonnull Ref<EntityStore> playerRef,
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

    public static void refreshCapacity(@Nonnull BloodState state,
                                       @Nonnull Ref<EntityStore> playerRef,
                                       @Nonnull Store<EntityStore> store) {
        state.maxBlood = resolveCapacityUnits(playerRef, store);
        if (state.blood > state.maxBlood) {
            state.blood = state.maxBlood;
        }
    }

    public static boolean isOverfed(@Nonnull Ref<EntityStore> playerRef) {
        return getBlood(playerRef) >= getMaxBlood(playerRef);
    }

    public static boolean isBloodStateNormal(@Nonnull Ref<EntityStore> playerRef) {
        int blood = getBlood(playerRef);
        return blood > VampirismConfig.get().getSatietyStarvingThreshold()
                && blood < getMaxBlood(playerRef);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
