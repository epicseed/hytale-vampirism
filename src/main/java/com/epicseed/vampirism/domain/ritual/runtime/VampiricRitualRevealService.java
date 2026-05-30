package com.epicseed.vampirism.domain.ritual.runtime;

import javax.annotation.Nonnull;

import java.util.List;

import com.epicseed.vampirism.domain.ritual.VampiricRitualActivationLink;
import com.epicseed.vampirism.domain.ritual.VampiricRitualPointState;
import com.epicseed.vampirism.domain.ritual.VampiricRitualRuntimeSnapshot;
import com.epicseed.vampirism.hytale.debug.VampiricDebugShapeRenderer;
import com.epicseed.vampirism.hytale.debug.VampiricRitualLineRenderer;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;

public final class VampiricRitualRevealService {

    public record RevealOptions(boolean showDebugGuides, boolean showActivationTimeline) {
        public static final RevealOptions FULL = new RevealOptions(true, true);
        public static final RevealOptions GAMEPLAY = new RevealOptions(false, true);
        public static final RevealOptions STROKE_ONLY = GAMEPLAY;
    }

    private static final float REVEAL_DURATION_SECONDS = 0.18f;
    private static final float ACTIVE_POINT_OPACITY = 0.28f;
    private static final float INACTIVE_POINT_OPACITY = 0.14f;
    private static final float RING_OPACITY = 0.12f;
    private static final double ACTIVE_POINT_SCALE = 0.24d;
    private static final double INACTIVE_POINT_SCALE = 0.16d;
    private static final double TRACE_POINT_SCALE = 0.055d;
    private static final double TRACE_POINT_FOCUS_SCALE = 0.085d;
    private static final double LIVE_TRACE_POINT_SCALE = 0.040d;
    private static final double LIVE_TRACE_TIP_SCALE = 0.085d;
    private static final double LINK_THICKNESS = 0.028d;
    private static final double TRACE_THICKNESS = 0.020d;
    private static final double LIVE_TRACE_THICKNESS = 0.030d;
    private static final double PILLAR_THICKNESS = 0.05d;
    private static final float STABLE_LINE_DURATION_SECONDS = 0.18f;
    private static final float LINK_OPACITY = 0.52f;
    private static final float TRACE_OPACITY = 0.48f;
    private static final float LIVE_TRACE_OPACITY = 0.82f;
    private static final float PILLAR_OPACITY = 0.42f;
    private static final float CROSS_OPACITY = 0.50f;
    private static final double LINK_ENERGY_SPHERE_SCALE = 0.070d;
    private static final double ACTIVE_LINK_PULSE_SCALE = 0.080d;

    private VampiricRitualRevealService() {
    }

    public static void reveal(@Nonnull World world, @Nonnull VampiricRitualRuntimeSnapshot snapshot) {
        reveal(world, snapshot, RevealOptions.FULL);
    }

    public static void reveal(@Nonnull World world,
                              @Nonnull VampiricRitualRuntimeSnapshot snapshot,
                              @Nonnull RevealOptions options) {
        double animationSeconds = System.currentTimeMillis() / 1000d;
        PhaseStyle style = PhaseStyle.of(snapshot);
        Vector3d anchor = snapshot.anchorCenter();
        if (options.showDebugGuides()) {
            VampiricDebugShapeRenderer.addCleanDisc(
                    world,
                    anchor.x,
                    anchor.y + 0.02d,
                    anchor.z,
                    style.ringRadius(),
                    style.ringColor(),
                    style.ringOpacity(),
                    REVEAL_DURATION_SECONDS,
                    0);
            VampiricDebugShapeRenderer.addCleanSphere(
                    world,
                    anchor.x,
                    anchor.y + 0.25d,
                    anchor.z,
                    style.coreColor(),
                    style.coreOpacity(),
                    style.coreScale(),
                    REVEAL_DURATION_SECONDS);
            drawAnchorState(world, snapshot, style, animationSeconds);
        }

        for (int index = 0; index < snapshot.pointStates().size(); index++) {
            VampiricRitualPointState point = snapshot.pointStates().get(index);
            if (options.showDebugGuides()) {
                var color = point.active() ? style.activePointColor() : style.inactivePointColor();
                float opacity = point.active() ? ACTIVE_POINT_OPACITY : INACTIVE_POINT_OPACITY;
                double scale = point.active() ? ACTIVE_POINT_SCALE : INACTIVE_POINT_SCALE;
                VampiricDebugShapeRenderer.addCleanSphere(
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
                    VampiricDebugShapeRenderer.addCleanSphere(
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
            drawLiveTrace(world, point, style, animationSeconds, options.showDebugGuides());
        }

        if (options.showActivationTimeline()) {
            drawActivationLinks(world, snapshot, style, animationSeconds);
        }

        if (options.showDebugGuides() && style.showPillar()) {
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

    private static void drawActivationLinks(@Nonnull World world,
                                            @Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                            @Nonnull PhaseStyle style,
                                            double animationSeconds) {
        List<VampiricRitualActivationLink> links = snapshot.activationLinks().isEmpty()
                ? fallbackLinks(snapshot.pointStates())
                : snapshot.activationLinks().stream()
                        .filter(link -> link.visibleAt(snapshot.channelProgressSeconds()))
                        .toList();
        for (VampiricRitualActivationLink link : links) {
            VampiricRitualPointState from = pointState(snapshot, link.fromPointId());
            VampiricRitualPointState to = pointState(snapshot, link.toPointId());
            if (from == null || to == null) {
                continue;
            }
            boolean activeLink = from.active() && to.active();
            double pulse = 1.0d + Math.sin(animationSeconds * (activeLink ? 6.5d : 4.2d) + link.startTimeSeconds()) * 0.18d;
            double thickness = LINK_THICKNESS * pulse;
            float opacity = (float) Math.max(0.20d, Math.min(0.86d, LINK_OPACITY * pulse));
            VampiricRitualLineRenderer.addBeam(
                    world,
                    from.position().x,
                    from.position().y + 0.08d,
                    from.position().z,
                    to.position().x,
                    to.position().y + 0.08d,
                    to.position().z,
                    activeLink ? DebugUtils.COLOR_RED : style.focusColor(),
                    thickness,
                    opacity,
                    STABLE_LINE_DURATION_SECONDS);
            double travel = animationProgress(animationSeconds * (activeLink ? 0.95d : 0.55d) + link.startTimeSeconds() * 0.33d);
            Vector3d focus = interpolate(from.position(), to.position(), travel);
            VampiricDebugShapeRenderer.addCleanSphere(
                    world,
                    focus.x,
                    focus.y + 0.10d,
                    focus.z,
                    activeLink ? DebugUtils.COLOR_RED : style.focusColor(),
                    activeLink ? 0.30f : 0.22f,
                    activeLink ? LINK_ENERGY_SPHERE_SCALE + ACTIVE_LINK_PULSE_SCALE * 0.2d : LINK_ENERGY_SPHERE_SCALE,
                    REVEAL_DURATION_SECONDS);
        }
    }

    @Nonnull
    private static List<VampiricRitualActivationLink> fallbackLinks(@Nonnull List<VampiricRitualPointState> points) {
        if (points.size() < 2) {
            return List.of();
        }
        java.util.ArrayList<VampiricRitualActivationLink> links = new java.util.ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            VampiricRitualPointState current = points.get(index);
            VampiricRitualPointState next = points.get((index + 1) % points.size());
            links.add(new VampiricRitualActivationLink(current.pointId(), next.pointId(), 0d, 0d));
        }
        return List.copyOf(links);
    }

    private static VampiricRitualPointState pointState(@Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                                       @Nonnull String pointId) {
        for (VampiricRitualPointState point : snapshot.pointStates()) {
            if (point.pointId().equals(pointId)) {
                return point;
            }
        }
        return null;
    }

    private static void drawLiveTrace(@Nonnull World world,
                                      @Nonnull VampiricRitualPointState point,
                                      @Nonnull PhaseStyle style,
                                      double animationSeconds,
                                      boolean showDebugGuides) {
        if (!point.tracing() || point.traceStrokePositions().isEmpty()) {
            return;
        }

        if (showDebugGuides) {
            Vector3d first = point.traceStrokePositions().get(0);
            VampiricRitualLineRenderer.addBeam(
                    world,
                    point.position().x,
                    point.position().y + 0.07d,
                    point.position().z,
                    first.x,
                    first.y + 0.07d,
                    first.z,
                    style.focusColor(),
                    LIVE_TRACE_THICKNESS,
                    LIVE_TRACE_OPACITY,
                    STABLE_LINE_DURATION_SECONDS);
        }

        for (int index = 0; index < point.traceStrokePositions().size(); index++) {
            Vector3d stroke = point.traceStrokePositions().get(index);
            boolean tip = index == point.traceStrokePositions().size() - 1;
            double tipPulse = tip ? 1.0d + Math.sin(animationSeconds * 12.0d) * 0.12d : 1.0d;
            if (showDebugGuides || tip) {
                VampiricDebugShapeRenderer.addCleanSphere(
                        world,
                        stroke.x,
                        stroke.y + 0.08d,
                        stroke.z,
                        style.activePointColor(),
                        tip ? (float) Math.min(0.30d, 0.24d * tipPulse) : 0.18f,
                        tip ? LIVE_TRACE_TIP_SCALE * tipPulse : LIVE_TRACE_POINT_SCALE,
                        REVEAL_DURATION_SECONDS);
            }
            if (index == 0) {
                continue;
            }
            Vector3d previous = point.traceStrokePositions().get(index - 1);
            VampiricRitualLineRenderer.addBeam(
                    world,
                    previous.x,
                    previous.y + 0.07d,
                    previous.z,
                    stroke.x,
                    stroke.y + 0.07d,
                    stroke.z,
                    style.activePointColor(),
                    LIVE_TRACE_THICKNESS,
                    LIVE_TRACE_OPACITY,
                    STABLE_LINE_DURATION_SECONDS);
        }
    }

    private static void drawAnchorState(@Nonnull World world,
                                        @Nonnull VampiricRitualRuntimeSnapshot snapshot,
                                        @Nonnull PhaseStyle style,
                                        double animationSeconds) {
        Vector3d anchor = snapshot.anchorCenter();
        switch (VampiricRitualAnchorState.fromSnapshot(snapshot)) {
            case PREPARED -> drawHorizontalCross(world, anchor, style.focusColor(), 0.55d, 0.18d, 0.04d);
            case BINDING -> drawHorizontalCross(world, anchor, style.focusColor(), 0.75d, 0.22d, 0.05d);
            case ACTIVE -> {
                VampiricDebugShapeRenderer.addCleanDisc(
                        world,
                        anchor.x,
                        anchor.y + Math.max(0.55d, snapshot.zoneHeight() * 0.45d),
                        anchor.z,
                        0.85d,
                        style.focusColor(),
                        0.12f,
                        REVEAL_DURATION_SECONDS,
                        0);
                VampiricDebugShapeRenderer.addCleanSphere(
                        world,
                        anchor.x,
                        anchor.y + 0.60d,
                        anchor.z,
                        style.focusColor(),
                        0.16f,
                        0.11d + Math.sin(animationSeconds * 6.0d) * 0.025d,
                        REVEAL_DURATION_SECONDS);
            }
            case UNSTABLE -> {
                VampiricDebugShapeRenderer.addCleanDisc(
                        world,
                        anchor.x,
                        anchor.y + 0.05d,
                        anchor.z,
                        3.7d,
                        DebugUtils.COLOR_YELLOW,
                        0.14f,
                        REVEAL_DURATION_SECONDS,
                        0);
                drawDiagonalCross(world, anchor, DebugUtils.COLOR_YELLOW, 0.95d, 0.18d, 0.06d);
                VampiricDebugShapeRenderer.addCleanSphere(
                        world,
                        anchor.x,
                        anchor.y + 0.42d,
                        anchor.z,
                        DebugUtils.COLOR_YELLOW,
                        0.18f,
                        0.13d + Math.sin(animationSeconds * 9.0d) * 0.03d,
                        REVEAL_DURATION_SECONDS);
            }
            case COLLAPSE -> {
                VampiricDebugShapeRenderer.addCleanDisc(
                        world,
                        anchor.x,
                        anchor.y + 0.03d,
                        anchor.z,
                        3.95d,
                        DebugUtils.COLOR_YELLOW,
                        0.10f,
                        REVEAL_DURATION_SECONDS,
                        0);
                drawDiagonalCross(world, anchor, DebugUtils.COLOR_RED, 1.25d, 0.16d, 0.07d);
                drawHorizontalCross(world, anchor, DebugUtils.COLOR_YELLOW, 0.70d, 0.12d, 0.05d);
                VampiricDebugShapeRenderer.addCleanSphere(
                        world,
                        anchor.x,
                        anchor.y + 0.28d,
                        anchor.z,
                        DebugUtils.COLOR_RED,
                        0.20f,
                        0.14d + Math.sin(animationSeconds * 8.2d) * 0.025d,
                        REVEAL_DURATION_SECONDS);
            }
            case COMPLETE -> {
                VampiricDebugShapeRenderer.addCleanDisc(
                        world,
                        anchor.x,
                        anchor.y + Math.max(0.6d, snapshot.zoneHeight() + 0.55d),
                        anchor.z,
                        0.95d,
                        DebugUtils.COLOR_MAGENTA,
                        0.12f,
                        REVEAL_DURATION_SECONDS,
                        0);
                VampiricDebugShapeRenderer.addCleanSphere(
                        world,
                        anchor.x,
                        anchor.y + Math.max(0.72d, snapshot.zoneHeight() + 0.58d),
                        anchor.z,
                        DebugUtils.COLOR_MAGENTA,
                        0.16f,
                        0.13d + Math.sin(animationSeconds * 6.4d) * 0.025d,
                        REVEAL_DURATION_SECONDS);
            }
        }
    }

    @Nonnull
    private static Vector3d interpolate(@Nonnull Vector3d start, @Nonnull Vector3d end, double alpha) {
        double clamped = Math.max(0.0d, Math.min(1.0d, alpha));
        return new Vector3d(
                start.x + (end.x - start.x) * clamped,
                start.y + (end.y - start.y) * clamped,
                start.z + (end.z - start.z) * clamped);
    }

    private static double animationProgress(double value) {
        return value - Math.floor(value);
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
                        0.18d,
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
                        0.24d,
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
                        0.28d,
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
                        0.32d,
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
                        0.34d,
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
                        0.30d,
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
