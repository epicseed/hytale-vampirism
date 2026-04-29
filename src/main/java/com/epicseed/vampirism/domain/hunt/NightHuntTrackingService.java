package com.epicseed.vampirism.domain.hunt;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class NightHuntTrackingService {
    private static final long PREY_HIT_CREDIT_WINDOW_MS = 5000L;

    private static final ConcurrentHashMap<UUID, UUID> PREY_OWNERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PreyHitRecord> PREY_LAST_HITS = new ConcurrentHashMap<>();

    private NightHuntTrackingService() {
    }

    public static void trackPrey(@Nonnull UUID preyUuid, @Nonnull UUID ownerUuid) {
        clearPrey(preyUuid);
        PREY_OWNERS.put(preyUuid, ownerUuid);
    }

    public static void clearPrey(@Nullable UUID preyUuid) {
        if (preyUuid == null) {
            return;
        }
        PREY_OWNERS.remove(preyUuid);
        PREY_LAST_HITS.remove(preyUuid);
    }

    @Nullable
    public static UUID ownerOf(@Nonnull UUID preyUuid) {
        return PREY_OWNERS.get(preyUuid);
    }

    public static void recordHit(@Nonnull UUID preyUuid, @Nonnull UUID attackerUuid) {
        UUID ownerUuid = PREY_OWNERS.get(preyUuid);
        if (ownerUuid == null || !ownerUuid.equals(attackerUuid)) {
            return;
        }
        PREY_LAST_HITS.put(preyUuid, new PreyHitRecord(attackerUuid, System.currentTimeMillis()));
    }

    public static boolean hasRecentOwnerHit(@Nonnull UUID preyUuid, @Nonnull UUID ownerUuid) {
        PreyHitRecord hitRecord = PREY_LAST_HITS.get(preyUuid);
        return hitRecord != null
                && ownerUuid.equals(hitRecord.attackerUuid())
                && System.currentTimeMillis() - hitRecord.hitAtMs() <= PREY_HIT_CREDIT_WINDOW_MS;
    }

    private record PreyHitRecord(@Nonnull UUID attackerUuid, long hitAtMs) {
    }
}
