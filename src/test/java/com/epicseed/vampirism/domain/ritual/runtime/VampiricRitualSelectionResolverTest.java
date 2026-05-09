package com.epicseed.vampirism.domain.ritual.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeService;

class VampiricRitualSelectionResolverTest {

    @Test
    void previewResolutionPrefersAttunedRitual() {
        UUID uuid = UUID.randomUUID();
        String anchorBlockId = "Furniture_Ancient_Coffin";
        VampiricRitualSelectionService selectionService = new VampiricRitualSelectionService();
        selectionService.select(uuid, anchorBlockId, "veil");

        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals = List.of(
                new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId),
                new VampiricRitualRuntimeService.ResolvedAnchorRitual("veil", "Veil", anchorBlockId));

        VampiricRitualRuntimeService.ResolvedAnchorRitual resolved =
                VampiricRitualSelectionResolver.resolveAttunedOrFirst(uuid, anchorBlockId, anchorRituals, selectionService);

        assertEquals("veil", resolved.ritualId());
    }

    @Test
    void previewResolutionFallsBackToFirstRitualWhenNoSelectionExists() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals = List.of(
                new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId),
                new VampiricRitualRuntimeService.ResolvedAnchorRitual("veil", "Veil", anchorBlockId));

        VampiricRitualRuntimeService.ResolvedAnchorRitual resolved =
                VampiricRitualSelectionResolver.resolveAttunedOrFirst(
                        UUID.randomUUID(),
                        anchorBlockId,
                        anchorRituals,
                        new VampiricRitualSelectionService());

        assertEquals("awakening", resolved.ritualId());
    }

    @Test
    void interactionResolutionRequiresAttunementWhenAnchorHasMultipleRituals() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals = List.of(
                new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId),
                new VampiricRitualRuntimeService.ResolvedAnchorRitual("veil", "Veil", anchorBlockId));

        VampiricRitualRuntimeService.ResolvedAnchorRitual resolved =
                VampiricRitualSelectionResolver.resolveAttunedOrSingle(
                        UUID.randomUUID(),
                        anchorBlockId,
                        anchorRituals,
                        new VampiricRitualSelectionService());

        assertNull(resolved);
    }

    @Test
    void interactionResolutionFallsBackToSingleRitual() {
        String anchorBlockId = "Furniture_Ancient_Coffin";
        List<VampiricRitualRuntimeService.ResolvedAnchorRitual> anchorRituals = List.of(
                new VampiricRitualRuntimeService.ResolvedAnchorRitual("awakening", "Awakening", anchorBlockId));

        VampiricRitualRuntimeService.ResolvedAnchorRitual resolved =
                VampiricRitualSelectionResolver.resolveAttunedOrSingle(
                        UUID.randomUUID(),
                        anchorBlockId,
                        anchorRituals,
                        new VampiricRitualSelectionService());

        assertEquals("awakening", resolved.ritualId());
    }
}
