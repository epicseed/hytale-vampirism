package com.epicseed.vampirism.domain.hunt;

import javax.annotation.Nonnull;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class GuideWispState {
    private final Ref<EntityStore> ref;
    private float liftRemainingSeconds;
    private Vector3d currentDirection;

    public GuideWispState(@Nonnull Ref<EntityStore> ref,
                          float liftRemainingSeconds,
                          @Nonnull Vector3d currentDirection) {
        this.ref = ref;
        this.liftRemainingSeconds = liftRemainingSeconds;
        this.currentDirection = currentDirection;
    }

    @Nonnull
    public Ref<EntityStore> ref() {
        return ref;
    }

    public float liftRemainingSeconds() {
        return liftRemainingSeconds;
    }

    public void liftRemainingSeconds(float liftRemainingSeconds) {
        this.liftRemainingSeconds = liftRemainingSeconds;
    }

    @Nonnull
    public Vector3d currentDirection() {
        return currentDirection;
    }

    public void currentDirection(@Nonnull Vector3d currentDirection) {
        this.currentDirection = currentDirection;
    }
}
