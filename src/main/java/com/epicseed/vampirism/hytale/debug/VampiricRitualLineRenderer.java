package com.epicseed.vampirism.hytale.debug;

import javax.annotation.Nonnull;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.world.World;

public final class VampiricRitualLineRenderer {

    public static final int DEFAULT_FLAGS = DebugUtils.FLAG_NO_WIREFRAME;
    private static final double MIN_SEGMENT_LENGTH = 0.001d;

    private VampiricRitualLineRenderer() {
    }

    public static void addBeam(@Nonnull World world,
                               @Nonnull Vector3d start,
                               @Nonnull Vector3d end,
                               @Nonnull Vector3f color,
                               double thickness,
                               float opacity,
                               float durationSeconds) {
        addBeam(world, start, end, color, thickness, opacity, durationSeconds, DEFAULT_FLAGS);
    }

    public static void addBeam(@Nonnull World world,
                               @Nonnull Vector3d start,
                               @Nonnull Vector3d end,
                               @Nonnull Vector3f color,
                               double thickness,
                               float opacity,
                               float durationSeconds,
                               int flags) {
        addBeam(
                world,
                start.x,
                start.y,
                start.z,
                end.x,
                end.y,
                end.z,
                color,
                thickness,
                opacity,
                durationSeconds,
                flags);
    }

    public static void addBeam(@Nonnull World world,
                               double startX,
                               double startY,
                               double startZ,
                               double endX,
                               double endY,
                               double endZ,
                               @Nonnull Vector3f color,
                               double thickness,
                               float opacity,
                               float durationSeconds) {
        addBeam(world, startX, startY, startZ, endX, endY, endZ, color, thickness, opacity, durationSeconds, DEFAULT_FLAGS);
    }

    public static void addBeam(@Nonnull World world,
                               double startX,
                               double startY,
                               double startZ,
                               double endX,
                               double endY,
                               double endZ,
                               @Nonnull Vector3f color,
                               double thickness,
                               float opacity,
                               float durationSeconds,
                               int flags) {
        double deltaX = endX - startX;
        double deltaY = endY - startY;
        double deltaZ = endZ - startZ;
        double length = Math.sqrt((deltaX * deltaX) + (deltaY * deltaY) + (deltaZ * deltaZ));
        if (length < MIN_SEGMENT_LENGTH) {
            return;
        }

        Matrix4d transform = new Matrix4d().identity();
        transform.translate(startX, startY, startZ);

        double yaw = Math.atan2(deltaZ, deltaX);
        transform.rotate(yaw + (Math.PI / 2.0d), 0.0d, 1.0d, 0.0d);

        double horizontalDistance = Math.sqrt((deltaX * deltaX) + (deltaZ * deltaZ));
        double pitch = Math.atan2(horizontalDistance, deltaY);
        transform.rotate(pitch, 1.0d, 0.0d, 0.0d);

        transform.translate(0.0d, length / 2.0d, 0.0d);
        transform.scale(thickness, length, thickness);
        DebugUtils.add(world, DebugShape.Cylinder, transform, color, opacity, durationSeconds, flags);
    }
}
