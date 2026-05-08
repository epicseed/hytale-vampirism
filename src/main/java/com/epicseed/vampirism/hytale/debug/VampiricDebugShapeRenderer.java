package com.epicseed.vampirism.hytale.debug;

import javax.annotation.Nonnull;

import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;

public final class VampiricDebugShapeRenderer {

    private VampiricDebugShapeRenderer() {
    }

    public static void addCleanSphere(@Nonnull World world,
                                      double centerX,
                                      double centerY,
                                      double centerZ,
                                      @Nonnull Vector3f color,
                                      float opacity,
                                      double scale,
                                      float durationSeconds) {
        addCleanSphere(world, centerX, centerY, centerZ, color, opacity, scale, durationSeconds, 0);
    }

    public static void addCleanSphere(@Nonnull World world,
                                      double centerX,
                                      double centerY,
                                      double centerZ,
                                      @Nonnull Vector3f color,
                                      float opacity,
                                      double scale,
                                      float durationSeconds,
                                      int flags) {
        Matrix4d transform = new Matrix4d().identity();
        transform.translate(centerX, centerY, centerZ);
        transform.scale(scale * 2.0d, scale * 2.0d, scale * 2.0d);
        DebugUtils.add(world, DebugShape.Sphere, transform, color, opacity, durationSeconds, flags | DebugUtils.FLAG_NO_WIREFRAME);
    }

    public static void addCleanDisc(@Nonnull World world,
                                    double centerX,
                                    double centerY,
                                    double centerZ,
                                    double radius,
                                    @Nonnull Vector3f color,
                                    float opacity,
                                    float durationSeconds) {
        addCleanDisc(world, centerX, centerY, centerZ, radius, color, opacity, durationSeconds, 0);
    }

    public static void addCleanDisc(@Nonnull World world,
                                    double centerX,
                                    double centerY,
                                    double centerZ,
                                    double radius,
                                    @Nonnull Vector3f color,
                                    float opacity,
                                    float durationSeconds,
                                    int flags) {
        DebugUtils.addDisc(
                world,
                centerX,
                centerY,
                centerZ,
                radius,
                color,
                opacity,
                durationSeconds,
                flags | DebugUtils.FLAG_NO_WIREFRAME);
    }
}
