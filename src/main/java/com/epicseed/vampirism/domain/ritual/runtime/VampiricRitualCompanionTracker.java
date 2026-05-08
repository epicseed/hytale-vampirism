package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class VampiricRitualCompanionTracker {

    private static final Map<UUID, CompanionState> ACTIVE_COMPANIONS = new ConcurrentHashMap<>();

    private VampiricRitualCompanionTracker() {
    }

    @Nullable
    public static CompanionState replace(@Nullable UUID ownerUuid,
                                         @Nullable Ref<EntityStore> companionRef,
                                         @Nullable String roleId,
                                         long expiresAtMs) {
        if (ownerUuid == null || companionRef == null || roleId == null || roleId.isBlank()) {
            return null;
        }
        return ACTIVE_COMPANIONS.put(
                ownerUuid,
                new CompanionState(companionRef, roleId.trim(), Math.max(0L, expiresAtMs)));
    }

    @Nonnull
    public static Optional<CompanionState> active(@Nullable UUID ownerUuid, long nowMs) {
        if (ownerUuid == null) {
            return Optional.empty();
        }
        CompanionState state = ACTIVE_COMPANIONS.get(ownerUuid);
        if (state == null) {
            return Optional.empty();
        }
        if (state.expiresAtMs() > 0L && nowMs >= state.expiresAtMs()) {
            ACTIVE_COMPANIONS.remove(ownerUuid, state);
            return Optional.empty();
        }
        return Optional.of(state);
    }

    @Nullable
    public static CompanionState expire(@Nullable UUID ownerUuid, long nowMs) {
        if (ownerUuid == null) {
            return null;
        }
        CompanionState state = ACTIVE_COMPANIONS.get(ownerUuid);
        if (state == null || state.expiresAtMs() <= 0L || nowMs < state.expiresAtMs()) {
            return null;
        }
        return ACTIVE_COMPANIONS.remove(ownerUuid, state) ? state : null;
    }

    @Nullable
    public static CompanionState clearPlayer(@Nullable UUID ownerUuid) {
        return ownerUuid != null ? ACTIVE_COMPANIONS.remove(ownerUuid) : null;
    }

    public record CompanionState(
            @Nonnull Ref<EntityStore> companionRef,
            @Nonnull String roleId,
            long expiresAtMs) {
    }
}
