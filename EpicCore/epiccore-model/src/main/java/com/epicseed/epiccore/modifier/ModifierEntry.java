package com.epicseed.epiccore.modifier;

/**
 * A registered modifier with its identity tag and evaluation priority.
 *
 * @param <C> the evaluation context type
 */
public record ModifierEntry<C>(ModifierTag tag, int priority, ValueModifier<C> modifier) {
}
