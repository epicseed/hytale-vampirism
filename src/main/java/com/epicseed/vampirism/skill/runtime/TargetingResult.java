package com.epicseed.vampirism.skill.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a targeting resolution.
 * Holds zero or more entity refs that an ability/action should act upon.
 */
public final class TargetingResult {

    private static final TargetingResult EMPTY = new TargetingResult(Collections.emptyList());

    private final List<Ref<EntityStore>> targets;

    private TargetingResult(@Nonnull List<Ref<EntityStore>> targets) {
        this.targets = targets;
    }

    @Nonnull
    public static TargetingResult empty() {
        return EMPTY;
    }

    @Nonnull
    public static TargetingResult of(@Nonnull Ref<EntityStore> target) {
        return new TargetingResult(List.of(target));
    }

    @Nonnull
    public static TargetingResult of(@Nonnull List<Ref<EntityStore>> targets) {
        if (targets.isEmpty()) return EMPTY;
        return new TargetingResult(List.copyOf(targets));
    }

    /** Returns true when at least one target was resolved. */
    public boolean hasTargets() {
        return !targets.isEmpty();
    }

    /** Returns an immutable list of resolved targets. */
    @Nonnull
    public List<Ref<EntityStore>> targets() {
        return targets;
    }

    /**
     * Returns the first resolved target, or {@code null} if there are none.
     * Useful for single-target abilities.
     */
    public Ref<EntityStore> first() {
        return targets.isEmpty() ? null : targets.get(0);
    }

    @Override
    public String toString() {
        return "TargetingResult{targets=" + targets.size() + "}";
    }
}
