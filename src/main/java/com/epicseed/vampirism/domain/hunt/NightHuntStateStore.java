package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.player.VampirePlayerStateStore;

public final class NightHuntStateStore {
    private static final ConcurrentHashMap<UUID, HuntState> ACTIVE_HUNTS = new ConcurrentHashMap<>();

    private NightHuntStateStore() {
    }

    @Nullable
    public static HuntState get(@Nullable UUID uuid) {
        return uuid != null ? ACTIVE_HUNTS.get(uuid) : null;
    }

    @Nonnull
    public static HuntState getOrCreate(@Nonnull UUID uuid, @Nonnull Supplier<Float> idleDelaySupplier) {
        return ACTIVE_HUNTS.computeIfAbsent(uuid, key -> createRestoredState(key, idleDelaySupplier));
    }

    @Nullable
    public static HuntState remove(@Nullable UUID uuid) {
        return uuid != null ? ACTIVE_HUNTS.remove(uuid) : null;
    }

    public static boolean resetCooldown(@Nullable UUID uuid, @Nonnull Supplier<Float> idleDelaySupplier) {
        if (uuid == null) {
            return false;
        }
        HuntState state = getOrCreate(uuid, idleDelaySupplier);
        state.cooldownRemainingSeconds = 0f;
        return true;
    }

    public static void captureDisconnectState(@Nullable UUID uuid, float failedCooldownSeconds) {
        HuntState state = get(uuid);
        if (uuid == null || state == null) {
            return;
        }

        long cooldownMs = secondsToMillis(state.cooldownRemainingSeconds);
        if (state.phase != HuntPhase.IDLE) {
            cooldownMs = Math.max(cooldownMs, secondsToMillis(failedCooldownSeconds));
        }
        VampirePlayerStateStore.get().setPersistedNightHuntCooldownMs(uuid, cooldownMs);
    }

    @Nonnull
    private static HuntState createRestoredState(@Nonnull UUID uuid, @Nonnull Supplier<Float> idleDelaySupplier) {
        HuntState state = new HuntState(idleDelaySupplier.get());
        long cooldownMs = VampirePlayerStateStore.get().getPersistedNightHuntCooldownMs(uuid);
        if (cooldownMs >= 0L) {
            state.cooldownRemainingSeconds = millisToSeconds(cooldownMs);
        }
        return state;
    }

    private static long secondsToMillis(float seconds) {
        if (seconds <= 0f) {
            return 0L;
        }
        return (long) Math.ceil(seconds * 1000.0d);
    }

    private static float millisToSeconds(long millis) {
        if (millis <= 0L) {
            return 0f;
        }
        return millis / 1000f;
    }
}
