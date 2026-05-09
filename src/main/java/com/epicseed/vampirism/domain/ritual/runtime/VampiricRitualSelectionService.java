package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VampiricRitualSelectionService {

    private final Map<UUID, Map<String, String>> selectionsByPlayer = new ConcurrentHashMap<>();

    public void select(@Nullable UUID uuid,
                       @Nullable String anchorBlockId,
                       @Nullable String ritualId) {
        if (uuid == null || anchorBlockId == null || anchorBlockId.isBlank()) {
            return;
        }
        if (ritualId == null || ritualId.isBlank()) {
            Map<String, String> perAnchor = selectionsByPlayer.get(uuid);
            if (perAnchor == null) {
                return;
            }
            perAnchor.remove(anchorBlockId.trim());
            if (perAnchor.isEmpty()) {
                selectionsByPlayer.remove(uuid);
            }
            return;
        }
        selectionsByPlayer
                .computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>())
                .put(anchorBlockId.trim(), ritualId.trim());
    }

    @Nonnull
    public Optional<String> selectedRitual(@Nullable UUID uuid, @Nullable String anchorBlockId) {
        if (uuid == null || anchorBlockId == null || anchorBlockId.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> perAnchor = selectionsByPlayer.get(uuid);
        if (perAnchor == null) {
            return Optional.empty();
        }
        String ritualId = perAnchor.get(anchorBlockId.trim());
        if (ritualId == null || ritualId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(ritualId);
    }

    @Nonnull
    public Map<String, String> snapshot(@Nullable UUID uuid) {
        if (uuid == null) {
            return Map.of();
        }
        Map<String, String> perAnchor = selectionsByPlayer.get(uuid);
        if (perAnchor == null || perAnchor.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(perAnchor));
    }

    public void clearPlayer(@Nullable UUID uuid) {
        if (uuid == null) {
            return;
        }
        selectionsByPlayer.remove(uuid);
    }
}
