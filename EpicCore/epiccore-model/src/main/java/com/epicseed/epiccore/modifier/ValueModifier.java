package com.epicseed.epiccore.modifier;

/**
 * A single modifier in a stat pipeline.
 *
 * @param <C> the evaluation context type
 */
@FunctionalInterface
public interface ValueModifier<C> {
    float apply(float current, C ctx);
}
