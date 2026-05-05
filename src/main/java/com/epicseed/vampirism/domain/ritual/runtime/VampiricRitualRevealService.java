package com.epicseed.vampirism.domain.ritual.runtime;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.hytale.debug.VampiricRitualLineRenderer;
import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;

public final class VampiricRitualRevealService {

    private static final float REVEAL_DURATION_SECONDS = 1.1f;
    private static final float ACTIVE_POINT_OPACITY = 0.28f;
    private static final float INACTIVE_POINT_OPACITY = 0.14f;
    private static final float RING_OPACITY = 0.12f;
    private static final double ACTIVE_POINT_SCALE = 0.42d;
    private static final double INACTIVE_POINT_SCALE = 0.30d;
    private static final double TRACE_POINT_SCALE = 0.12d;
    private static final double TRACE_POINT_FOCUS_SCALE = 0.18d;
    private static final double LINK_THICKNESS = 0.05d;
    private static final double TRACE_THICKNESS = 0.035d;
    private static final double PILLAR_THICKNESS = 0.08d;
    private static final float STABLE_LINE_DURATION_SECONDS = 0.9f;
    private static final float LINK_OPACITY = 0.52f;
    private static final float TRACE_OPACITY = 0.48f;
    private static final float PILLAR_OPACITY = 0.42f;
    private static final float CROSS_OPACITY = 0.50f;

    private VampiricRitualRevealService() {
    }

    public static void reveal(@Nonnull World world, @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        PhaseStyle style = PhaseStyle.of(snapshot);
        Vector3d anchor = snapshot.anchorCenter();
        DebugUtils.addDisc(
                world,
                anchor.x,
                anchor.y + 0.02d,
                anchor.z,
                style.ringRadius(),
                style.ringColor(),
                style.ringOpacity(),
                REVEAL_DURATION_SECONDS,
                DebugUtils.FLAG_FADE);
        DebugUtils.addSphere(
                world,
                anchor.x,
                anchor.y + 0.25d,
                anchor.z,
                style.coreColor(),
                style.coreOpacity(),
                style.coreScale(),
                REVEAL_DURATION_SECONDS);
        drawAnchorState(world, snapshot, style);

        for (int index = 0; index < snapshot.pointStates().size(); index++) {
            VampiricRitualPointState point = snapshot.pointStates().get(index);
            VampiricRitualPointState next = snapshot.pointStates().get((index + 1) % snapshot.pointStates().size());
            var color = point.active() ? style.activePointColor() : style.inactivePointColor();
            float opacity = point.active() ? ACTIVE_POINT_OPACITY : INACTIVE_POINT_OPACITY;
            double scale = point.active() ? ACTIVE_POINT_SCALE : INACTIVE_POINT_SCALE;
            DebugUtils.addSphere(
                    world,
                    point.position().x,
                    point.position().y + 0.2d,
                    point.position().z,
                    color,
                    opacity,
                    scale,
                    REVEAL_DURATION_SECONDS);
            VampiricRitualLineRenderer.addBeam(
                    world,
                    anchor.x,
                    anchor.y + 0.12d,
                    anchor.z,
                    point.position().x,
                    point.position().y + 0.12d,
                    point.position().z,
                    color,
                    LINK_THICKNESS,
                    LINK_OPACITY,
                    STABLE_LINE_DURATION_SECONDS);
            VampiricRitualLineRenderer.addBeam(
                    world,
                    point.position().x,
                    point.position().y + 0.08d,
                    point.position().z,
                    next.position().x,
                    next.position().y + 0.08d,
                    next.position().z,
                    point.active() && next.active() ? DebugUtils.COLOR_RED : DebugUtils.COLOR_YELLOW,
                    LINK_THICKNESS,
                    LINK_OPACITY,
                    STABLE_LINE_DURATION_SECONDS);

            for (int stepIndex = 0; stepIndex < point.traceStepPositions().size(); stepIndex++) {
                Vector3d step = point.traceStepPositions().get(stepIndex);
                boolean completedStep = point.active() || stepIndex < point.traceProgress();
                boolean focusStep = !point.active()
                        && ((point.tracing() && stepIndex == point.traceProgress()) || (!point.tracing() && stepIndex == 0));
                var stepColor = completedStep
                        ? style.activePointColor()
                        : focusStep ? style.focusColor() : style.inactivePointColor();
                double stepScale = focusStep ? TRACE_POINT_FOCUS_SCALE : TRACE_POINT_SCALE;
                float stepOpacity = completedStep ? 0.30f : focusStep ? 0.24f : 0.16f;
                DebugUtils.addSphere(
                        world,
                        step.x,
                        step.y + 0.08d,
                        step.z,
                        stepColor,
                        stepOpacity,
                        stepScale,
                        REVEAL_DURATION_SECONDS);
                if (stepIndex == 0) {
                    VampiricRitualLineRenderer.addBeam(
                            world,
                            point.position().x,
                            point.position().y + 0.06d,
                            point.position().z,
                            step.x,
                            step.y + 0.06d,
                            step.z,
                            stepColor,
                            TRACE_THICKNESS,
                            TRACE_OPACITY,
                            STABLE_LINE_DURATION_SECONDS);
                    continue;
                }
                Vector3d previous = point.traceStepPositions().get(stepIndex - 1);
                boolean energizedSegment = point.active()
                        || (point.tracing() && stepIndex <= point.traceProgress())
                        || (!point.tracing() && stepIndex == 1);
                VampiricRitualLineRenderer.addBeam(
                        world,
                        previous.x,
                        previous.y + 0.06d,
                        previous.z,
                        step.x,
                        step.y + 0.06d,
                        step.z,
                        energizedSegment ? stepColor : style.inactivePointColor(),
                        TRACE_THICKNESS,
                        TRACE_OPACITY,
                        STABLE_LINE_DURATION_SECONDS);
            }
        }

        if (style.showPillar()) {
            VampiricRitualLineRenderer.addBeam(
                    world,
                    anchor.x,
                    anchor.y + 0.08d,
                    anchor.z,
                    anchor.x,
                    anchor.y + style.pillarHeight(snapshot),
                    anchor.z,
                    style.pillarColor(),
                    PILLAR_THICKNESS,
                    PILLAR_OPACITY,
                    STABLE_LINE_DURATION_SECONDS);
        }
    }

    private static void drawAnchorState(@Nonnull World world,
                                        @Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                        @Nonnull PhaseStyle style) {
        Vector3d anchor = snapshot.anchorCenter();
        switch (VampiricRitualAnchorState.fromSnapshot(snapshot)) {
            case PREPARED -> drawHorizontalCross(world, anchor, style.focusColor(), 0.55d, 0.18d, 0.04d);
            case BINDING -> drawHorizontalCross(world, anchor, style.focusColor(), 0.75d, 0.22d, 0.05d);
            case ACTIVE -> {
                DebugUtils.addDisc(
                        world,
                        anchor.x,
                        anchor.y + Math.max(0.55d, snapshot.zoneHeight() * 0.45d),
                        anchor.z,
                        0.85d,
                        style.focusColor(),
                        0.12f,
                        REVEAL_DURATION_SECONDS,
                        DebugUtils.FLAG_FADE);
            }
            case UNSTABLE -> {
                DebugUtils.addDisc(
                        world,
                        anchor.x,
                        anchor.y + 0.05d,
                        anchor.z,
                        3.7d,
                        DebugUtils.COLOR_YELLOW,
                        0.14f,
                        REVEAL_DURATION_SECONDS,
                        DebugUtils.FLAG_FADE);
                drawDiagonalCross(world, anchor, DebugUtils.COLOR_YELLOW, 0.95d, 0.18d, 0.06d);
            }
            case COLLAPSE -> {
                DebugUtils.addDisc(
                        world,
                        anchor.x,
                        anchor.y + 0.03d,
                        anchor.z,
                        3.95d,
                        DebugUtils.COLOR_YELLOW,
                        0.10f,
                        REVEAL_DURATION_SECONDS,
                        DebugUtils.FLAG_FADE);
                drawDiagonalCross(world, anchor, DebugUtils.COLOR_RED, 1.25d, 0.16d, 0.07d);
                drawHorizontalCross(world, anchor, DebugUtils.COLOR_YELLOW, 0.70d, 0.12d, 0.05d);
            }
            case COMPLETE -> {
                DebugUtils.addDisc(
                        world,
                        anchor.x,
                        anchor.y + Math.max(0.6d, snapshot.zoneHeight() + 0.55d),
                        anchor.z,
                        0.95d,
                        DebugUtils.COLOR_MAGENTA,
                        0.12f,
                        REVEAL_DURATION_SECONDS,
                        DebugUtils.FLAG_FADE);
            }
        }
    }

    private static void drawHorizontalCross(@Nonnull World world,
                                            @Nonnull Vector3d anchor,
                                            @Nonnull Vector3f color,
                                            double radius,
                                            double yOffset,
                                            double thickness) {
        VampiricRitualLineRenderer.addBeam(
                world,
                anchor.x - radius,
                anchor.y + yOffset,
                anchor.z,
                anchor.x + radius,
                anchor.y + yOffset,
                anchor.z,
                color,
                thickness,
                CROSS_OPACITY,
                STABLE_LINE_DURATION_SECONDS);
        VampiricRitualLineRenderer.addBeam(
                world,
                anchor.x,
                anchor.y + yOffset,
                anchor.z - radius,
                anchor.x,
                anchor.y + yOffset,
                anchor.z + radius,
                color,
                thickness,
                CROSS_OPACITY,
                STABLE_LINE_DURATION_SECONDS);
    }

    private static void drawDiagonalCross(@Nonnull World world,
                                          @Nonnull Vector3d anchor,
                                          @Nonnull Vector3f color,
                                          double radius,
                                          double yOffset,
                                          double thickness) {
        VampiricRitualLineRenderer.addBeam(
                world,
                anchor.x - radius,
                anchor.y + yOffset,
                anchor.z - radius,
                anchor.x + radius,
                anchor.y + yOffset,
                anchor.z + radius,
                color,
                thickness,
                CROSS_OPACITY,
                STABLE_LINE_DURATION_SECONDS);
        VampiricRitualLineRenderer.addBeam(
                world,
                anchor.x - radius,
                anchor.y + yOffset,
                anchor.z + radius,
                anchor.x + radius,
                anchor.y + yOffset,
                anchor.z - radius,
                color,
                thickness,
                CROSS_OPACITY,
                STABLE_LINE_DURATION_SECONDS);
    }

    private record PhaseStyle(@Nonnull Vector3f ringColor,
                              float ringOpacity,
                              double ringRadius,
                              @Nonnull Vector3f coreColor,
                              float coreOpacity,
                              double coreScale,
                              @Nonnull Vector3f activePointColor,
                              @Nonnull Vector3f inactivePointColor,
                              @Nonnull Vector3f focusColor,
                              boolean showPillar,
                              @Nonnull Vector3f pillarColor) {

        @Nonnull
        private static PhaseStyle of(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
            return switch (VampiricRitualAnchorState.fromSnapshot(snapshot)) {
                case PREPARED -> new PhaseStyle(
                        DebugUtils.COLOR_MAGENTA,
                        0.10f,
                        3.25d,
                        DebugUtils.COLOR_MAGENTA,
                        0.14f,
                        0.30d,
                        DebugUtils.COLOR_RED,
                        DebugUtils.COLOR_YELLOW,
                        DebugUtils.COLOR_MAGENTA,
                        false,
                        DebugUtils.COLOR_MAGENTA);
                case BINDING -> new PhaseStyle(
                        DebugUtils.COLOR_MAGENTA,
                        0.14f,
                        3.4d,
                        DebugUtils.COLOR_MAGENTA,
                        0.20f,
                        0.44d,
                        DebugUtils.COLOR_RED,
                        DebugUtils.COLOR_YELLOW,
                        DebugUtils.COLOR_MAGENTA,
                        true,
                        DebugUtils.COLOR_MAGENTA);
                case ACTIVE -> new PhaseStyle(
                        DebugUtils.COLOR_RED,
                        0.16f,
                        3.55d,
                        DebugUtils.COLOR_RED,
                        0.22f,
                        0.48d,
                        DebugUtils.COLOR_RED,
                        DebugUtils.COLOR_MAGENTA,
                        DebugUtils.COLOR_MAGENTA,
                        true,
                        DebugUtils.COLOR_MAGENTA);
                case UNSTABLE -> new PhaseStyle(
                        DebugUtils.COLOR_YELLOW,
                        0.14f,
                        3.7d,
                        DebugUtils.COLOR_YELLOW,
                        0.24f,
                        0.54d,
                        DebugUtils.COLOR_RED,
                        DebugUtils.COLOR_YELLOW,
                        DebugUtils.COLOR_YELLOW,
                        true,
                        DebugUtils.COLOR_YELLOW);
                case COLLAPSE -> new PhaseStyle(
                        DebugUtils.COLOR_YELLOW,
                        0.12f,
                        3.95d,
                        DebugUtils.COLOR_RED,
                        0.26f,
                        0.58d,
                        DebugUtils.COLOR_RED,
                        DebugUtils.COLOR_YELLOW,
                        DebugUtils.COLOR_YELLOW,
                        true,
                        DebugUtils.COLOR_RED);
                case COMPLETE -> new PhaseStyle(
                        DebugUtils.COLOR_RED,
                        0.16f,
                        3.6d,
                        DebugUtils.COLOR_RED,
                        0.22f,
                        0.50d,
                        DebugUtils.COLOR_RED,
                        DebugUtils.COLOR_MAGENTA,
                        DebugUtils.COLOR_MAGENTA,
                        true,
                        DebugUtils.COLOR_MAGENTA);
            };
        }

        private double pillarHeight(@Nonnull VampiricRitualRuntimeSnapshot snapshot) {
            return switch (VampiricRitualAnchorState.fromSnapshot(snapshot)) {
                case PREPARED -> 0.9d;
                case BINDING -> Math.max(1.15d, snapshot.zoneHeight() + 0.65d);
                case ACTIVE -> Math.max(1.7d, snapshot.zoneHeight() + 1.0d);
                case UNSTABLE -> Math.max(1.45d, snapshot.zoneHeight() + 0.85d);
                case COLLAPSE -> 0.95d;
                case COMPLETE -> Math.max(1.8d, snapshot.zoneHeight() + 1.1d);
            };
        }
    }
}
