package com.epicseed.epiccore.skill.runtime;

import java.util.Collections;
import java.util.List;

public final class ResolvedTargets<T> {

    private static final ResolvedTargets<?> EMPTY = new ResolvedTargets<>(Collections.emptyList());

    private final List<T> targets;

    private ResolvedTargets(List<T> targets) {
        this.targets = targets;
    }

    @SuppressWarnings("unchecked")
    public static <T> ResolvedTargets<T> empty() {
        return (ResolvedTargets<T>) EMPTY;
    }

    public static <T> ResolvedTargets<T> of(T target) {
        return new ResolvedTargets<>(List.of(target));
    }

    public static <T> ResolvedTargets<T> of(List<T> targets) {
        if (targets.isEmpty()) {
            return empty();
        }
        return new ResolvedTargets<>(List.copyOf(targets));
    }

    public boolean hasTargets() {
        return !targets.isEmpty();
    }

    public List<T> targets() {
        return targets;
    }

    public T first() {
        return targets.isEmpty() ? null : targets.get(0);
    }
}
