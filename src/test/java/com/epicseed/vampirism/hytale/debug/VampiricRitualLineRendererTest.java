package com.epicseed.vampirism.hytale.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

final class VampiricRitualLineRendererTest {

    @Test
    void beamTransformCentersCylinderBetweenStartAndPositiveZEnd() {
        Matrix4d transform = VampiricRitualLineRenderer.beamTransform(
                1.0d, 2.0d, 3.0d,
                0.0d, 0.0d, 4.0d,
                4.0d,
                0.03d);

        Vector3d center = transform.transformPosition(new Vector3d(0.0d, 0.0d, 0.0d));

        assertEquals(1.0d, center.x, 0.0001d);
        assertEquals(2.0d, center.y, 0.0001d);
        assertEquals(5.0d, center.z, 0.0001d);
    }

    @Test
    void beamTransformCentersCylinderBetweenStartAndPositiveXEnd() {
        Matrix4d transform = VampiricRitualLineRenderer.beamTransform(
                1.0d, 2.0d, 3.0d,
                4.0d, 0.0d, 0.0d,
                4.0d,
                0.03d);

        Vector3d center = transform.transformPosition(new Vector3d(0.0d, 0.0d, 0.0d));

        assertEquals(3.0d, center.x, 0.0001d);
        assertEquals(2.0d, center.y, 0.0001d);
        assertEquals(3.0d, center.z, 0.0001d);
    }
}
