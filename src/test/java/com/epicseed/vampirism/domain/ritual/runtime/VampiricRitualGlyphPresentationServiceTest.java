package com.epicseed.vampirism.domain.ritual.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualRegistry;
import com.epicseed.vampirism.domain.ritual.VampiricRitualAnchorLayer;
import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import org.joml.Vector3d;
import org.joml.Vector3i;

class VampiricRitualGlyphPresentationServiceTest {

    @Test
    void describesAnchorAndPerPointLayers() {
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout layout =
                VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.PREPARING,
                        List.of(
                                point("north", true, "fang_wake", new Vector3d(0d, 0.15d, -3d)),
                                point("south", false, "vein_eye", new Vector3d(0d, 0.15d, 3d)))), 0.0d);

        assertEquals(6, layout.visuals().size());
        assertEquals("anchor/base", layout.visuals().get(0).key());
        assertEquals("anchor/core", layout.visuals().get(1).key());
        assertTrue(layout.visuals().stream().anyMatch(visual -> visual.key().equals("point/north/node")));
        assertTrue(layout.visuals().stream().anyMatch(visual -> visual.key().equals("point/south/symbol")));
    }

    @Test
    void highlightsActiveNodesAndFallsBackForUnknownSymbols() {
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout layout =
                VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.BINDING,
                        List.of(
                                point("north", true, "unknown_symbol", new Vector3d(0d, 0.15d, -3d)),
                                point("west", false, "moon_scar", new Vector3d(-3d, 0.15d, 0d)))), 0.0d);

        VampiricRitualGlyphPresentationService.GlyphVisualSpec activeNode = visual(layout, "point/north/node");
        VampiricRitualGlyphPresentationService.GlyphVisualSpec inactiveNode = visual(layout, "point/west/node");
        VampiricRitualGlyphPresentationService.GlyphVisualSpec fallbackSymbol = visual(layout, "point/north/symbol");

        assertEquals(VampiricRitualGlyphPresentationService.NODE_ACTIVE_PROJECTILE_ID, activeNode.projectileId());
        assertEquals(VampiricRitualGlyphPresentationService.NODE_INACTIVE_PROJECTILE_ID, inactiveNode.projectileId());
        assertEquals(VampiricRitualGlyphPresentationService.GENERIC_SYMBOL_PROJECTILE_ID, fallbackSymbol.projectileId());
        assertTrue(fallbackSymbol.position().y() > activeNode.position().y());
    }

    @Test
    void keepsGroundGlyphsFlatAndSeparatesSymbolLayer() {
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout layout =
                VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.PREPARING,
                        List.of(point("east", false, "fang_wake", new Vector3d(3d, 0.15d, 0d)))), 0.0d);

        VampiricRitualGlyphPresentationService.GlyphVisualSpec node = visual(layout, "point/east/node");
        VampiricRitualGlyphPresentationService.GlyphVisualSpec symbol = visual(layout, "point/east/symbol");

        assertEquals(0.0f, node.rotation().pitch());
        assertEquals(0.0f, symbol.rotation().pitch());
        assertTrue(symbol.position().y() - node.position().y() >= 0.045d);
    }

    @Test
    void switchesAnchorCoreByAnchorState() {
        assertEquals(
                VampiricRitualGlyphPresentationService.CORE_CALM_PROJECTILE_ID,
                visual(VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.PREPARING,
                        List.of(point("north", false, "fang_wake", new Vector3d(0d, 0.15d, -3d)))), 0.0d), "anchor/core")
                        .projectileId());
        assertEquals(
                VampiricRitualGlyphPresentationService.CORE_ACTIVE_PROJECTILE_ID,
                visual(VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.CHANNELING,
                        List.of(point("north", true, "fang_wake", new Vector3d(0d, 0.15d, -3d)))), 0.0d), "anchor/core")
                        .projectileId());
        assertEquals(
                VampiricRitualGlyphPresentationService.CORE_UNSTABLE_PROJECTILE_ID,
                visual(VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.COLLAPSE,
                        List.of(point("north", false, "fang_wake", new Vector3d(0d, 0.15d, -3d)))), 0.0d), "anchor/core")
                        .projectileId());
    }

    @Test
    void hidesTracingSymbolButKeepsNodeReference() {
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout tracingLayout =
                VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.PREPARING,
                        List.of(tracingPoint("north", "fang_wake", new Vector3d(0d, 0.15d, -3d)))), 1.0d);

        assertTrue(tracingLayout.visuals().stream().noneMatch(visual -> visual.key().equals("point/north/symbol")));
        assertTrue(tracingLayout.visuals().stream().anyMatch(visual -> visual.key().equals("point/north/node")));
    }

    @Test
    void rendersConfigurableAnchorLayerOffsets() {
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout layout =
                VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.CHANNELING,
                        anchorLayer(0.03d),
                        anchorLayer(0.12d),
                        List.of(point("north", true, "fang_wake", new Vector3d(0d, 0.15d, -3d)))), 0.0d);

        assertEquals(0.03d, visual(layout, "anchor/base").position().y(), 0.0001d);
        assertEquals(0.12d, visual(layout, "anchor/core").position().y(), 0.0001d);
    }

    @Test
    void rendersOfferingOverlaysOnCenterAndPoints() {
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout layout =
                VampiricRitualGlyphPresentationService.describe(
                        snapshot(
                                VampiricRitualRuntimePhase.PREPARING,
                                List.of(point("north", false, "fang_wake", new Vector3d(0d, 0.15d, -3d)))),
                        Map.of(
                                "center", VampiricRitualRegistry.VOID_HEART_ITEM_ID,
                                "point:north", VampiricRitualRegistry.VOID_HEART_ITEM_ID),
                        0.0d);

        VampiricRitualGlyphPresentationService.GlyphVisualSpec center = visual(layout, "offering/center");
        VampiricRitualGlyphPresentationService.GlyphVisualSpec north = visual(layout, "offering/point:north");

        assertEquals(VampiricRitualGlyphPresentationService.VOID_HEART_PROJECTILE_ID, center.projectileId());
        assertEquals(VampiricRitualGlyphPresentationService.VOID_HEART_PROJECTILE_ID, north.projectileId());
        assertTrue(center.position().y() - visual(layout, "anchor/core").position().y() >= 0.2d);
        assertTrue(north.position().y() - visual(layout, "point/north/node").position().y() >= 0.2d);
    }

    @Test
    void animatesOfferingOverlaysWithLiftAndSpin() {
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout start =
                VampiricRitualGlyphPresentationService.describe(
                        snapshot(
                                VampiricRitualRuntimePhase.CHANNELING,
                                List.of(point("north", true, "fang_wake", new Vector3d(0d, 0.15d, -3d)))),
                        Map.of("point:north", VampiricRitualRegistry.VOID_HEART_ITEM_ID),
                        0.0d);
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout animated =
                VampiricRitualGlyphPresentationService.describe(
                        snapshot(
                                VampiricRitualRuntimePhase.CHANNELING,
                                List.of(point("north", true, "fang_wake", new Vector3d(0d, 0.15d, -3d)))),
                        Map.of("point:north", VampiricRitualRegistry.VOID_HEART_ITEM_ID),
                        1.0d);

        VampiricRitualGlyphPresentationService.GlyphVisualSpec startOffering = visual(start, "offering/point:north");
        VampiricRitualGlyphPresentationService.GlyphVisualSpec animatedOffering = visual(animated, "offering/point:north");

        assertNotEquals(startOffering.position().y(), animatedOffering.position().y());
        assertNotEquals(startOffering.rotation().yaw(), animatedOffering.rotation().yaw());
    }

    @Test
    void exposesOfferingSurfaceInteractionUxDefaults() {
        assertEquals(
                "server.interactionHints.placeOffering",
                VampiricRitualGlyphPresentationService.OFFERING_SURFACE_PLACE_INTERACTION_HINT);
        assertEquals(
                "server.interactionHints.reclaimOffering",
                VampiricRitualGlyphPresentationService.OFFERING_SURFACE_RECLAIM_INTERACTION_HINT);
        assertEquals(
                VampiricRitualGlyphPresentationService.OFFERING_SURFACE_PLACE_INTERACTION_HINT,
                VampiricRitualGlyphPresentationService.offeringSurfaceInteractionHint(false));
        assertEquals(
                VampiricRitualGlyphPresentationService.OFFERING_SURFACE_RECLAIM_INTERACTION_HINT,
                VampiricRitualGlyphPresentationService.offeringSurfaceInteractionHint(true));
        assertTrue(VampiricRitualGlyphPresentationService.OFFERING_SURFACE_HALF_WIDTH <= 1.2d);
        assertTrue(VampiricRitualGlyphPresentationService.OFFERING_SURFACE_HEIGHT <= 1.1d);
    }

    private static VampiricRitualGlyphPresentationService.GlyphVisualSpec visual(
            VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout layout,
            String key) {
        return layout.visuals().stream()
                .filter(visual -> visual.key().equals(key))
                .findFirst()
                .orElseThrow();
    }

    private static VampiricRitualRuntimeSnapshot snapshot(VampiricRitualRuntimePhase phase,
                                                          List<VampiricRitualPointState> points) {
        return snapshot(phase, null, null, points);
    }

    private static VampiricRitualRuntimeSnapshot snapshot(VampiricRitualRuntimePhase phase,
                                                          VampiricRitualAnchorLayer baseLayer,
                                                          VampiricRitualAnchorLayer coreLayer,
                                                          List<VampiricRitualPointState> points) {
        long activePoints = points.stream().filter(VampiricRitualPointState::active).count();
        return new VampiricRitualRuntimeSnapshot(
                "awakening",
                "Crimson Awakening",
                "Furniture_Ancient_Coffin",
                new Vector3i(0, 0, 0),
                new Vector3d(0d, 0d, 0d),
                baseLayer,
                coreLayer,
                phase,
                phase.active(),
                (int) activePoints,
                points.size(),
                80d,
                65d,
                20d,
                1.5d,
                2,
                2d,
                8d,
                List.of(),
                points);
    }

    private static VampiricRitualAnchorLayer anchorLayer(double offsetY) {
        return new VampiricRitualAnchorLayer(offsetY);
    }

    private static VampiricRitualPointState point(String id,
                                                  boolean active,
                                                  String symbolId,
                                                  Vector3d position) {
        return new VampiricRitualPointState(
                id,
                position,
                active,
                0.9d,
                symbolId,
                symbolId,
                active ? 4 : 1,
                4,
                false,
                List.of(new Vector3d(position)),
                List.of());
    }

    private static VampiricRitualPointState tracingPoint(String id,
                                                         String symbolId,
                                                         Vector3d position) {
        return new VampiricRitualPointState(
                id,
                position,
                false,
                0.68d,
                symbolId,
                symbolId,
                1,
                4,
                true,
                List.of(new Vector3d(position), new Vector3d(position).add(0.2d, 0.0d, 0.0d)),
                List.of(new Vector3d(position), new Vector3d(position).add(0.15d, 0.0d, 0.0d)));
    }
}
