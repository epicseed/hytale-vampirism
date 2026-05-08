package com.epicseed.vampirism.domain.ritual.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.epicseed.vampirism.domain.ritual.VampiricRitualAnchorLayer;
import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimePhase;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;

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
        assertTrue(fallbackSymbol.position().getY() > activeNode.position().getY());
    }

    @Test
    void keepsGroundGlyphsFlatAndSeparatesSymbolLayer() {
        VampiricRitualGlyphPresentationService.RitualGlyphPresentationLayout layout =
                VampiricRitualGlyphPresentationService.describe(snapshot(
                        VampiricRitualRuntimePhase.PREPARING,
                        List.of(point("east", false, "fang_wake", new Vector3d(3d, 0.15d, 0d)))), 0.0d);

        VampiricRitualGlyphPresentationService.GlyphVisualSpec node = visual(layout, "point/east/node");
        VampiricRitualGlyphPresentationService.GlyphVisualSpec symbol = visual(layout, "point/east/symbol");

        assertEquals(0.0f, node.rotation().getPitch());
        assertEquals(0.0f, symbol.rotation().getPitch());
        assertTrue(symbol.position().getY() - node.position().getY() >= 0.045d);
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

        assertEquals(0.03d, visual(layout, "anchor/base").position().getY(), 0.0001d);
        assertEquals(0.12d, visual(layout, "anchor/core").position().getY(), 0.0001d);
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
