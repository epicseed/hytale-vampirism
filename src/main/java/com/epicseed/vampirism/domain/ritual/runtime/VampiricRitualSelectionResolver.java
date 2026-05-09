package com.epicseed.vampirism.domain.ritual.runtime;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;

public final class VampiricRitualSelectionResolver {

    private VampiricRitualSelectionResolver() {
    }

    @Nullable
    public static VampiricRitualRuntimeService.ResolvedAnchorRitual resolveAttunedOrFirst(
            @Nullable UUID uuid,
            @Nullable String anchorBlockId,
            @Nullable List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals,
            @Nonnull VampiricRitualSelectionService selectionService) {
        VampiricRitualRuntimeService.ResolvedAnchorRitual selected =
                resolveAttuned(uuid, anchorBlockId, anchorRituals, selectionService);
        if (selected != null) {
            return selected;
        }
        if (anchorRituals == null || anchorRituals.isEmpty()) {
            return null;
        }
        return anchorRituals.get(0);
    }

    @Nullable
    public static VampiricRitualRuntimeService.ResolvedAnchorRitual resolveAttunedOrSingle(
            @Nullable UUID uuid,
            @Nullable String anchorBlockId,
            @Nullable List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals,
            @Nonnull VampiricRitualSelectionService selectionService) {
        VampiricRitualRuntimeService.ResolvedAnchorRitual selected =
                resolveAttuned(uuid, anchorBlockId, anchorRituals, selectionService);
        if (selected != null) {
            return selected;
        }
        return anchorRituals != null && anchorRituals.size() == 1 ? anchorRituals.get(0) : null;
    }

    @Nullable
    private static VampiricRitualRuntimeService.ResolvedAnchorRitual resolveAttuned(
            @Nullable UUID uuid,
            @Nullable String anchorBlockId,
            @Nullable List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals,
            @Nonnull VampiricRitualSelectionService selectionService) {
        Objects.requireNonNull(selectionService, "selectionService");
        if (anchorRituals == null || anchorRituals.isEmpty()) {
            return null;
        }
        String ritualId = selectionService.selectedRitual(uuid, anchorBlockId).orElse(null);
        if (ritualId == null) {
            return null;
        }
        for (VampiricRitualRuntimeService.ResolvedAnchorRitual ritual : anchorRituals) {
            if (ritualId.equals(ritual.ritualId())) {
                return ritual;
            }
        }
        return null;
    }
}
