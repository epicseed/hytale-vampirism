package com.epicseed.vampirism.modifier;

/**
 * A single modifier in a stat pipeline.
 *
 * <p>Receives the value accumulated so far by previous modifiers and returns the
 * new value. Modifiers run in ascending priority order.
 *
 * <p>Examples:
 * <pre>
 *   // Additive: add a flat bonus
 *   (current, ctx) -> current + 0.15f
 *
 *   // Multiplicative: scale by 1.3
 *   (current, ctx) -> current * 1.3f
 *
 *   // Conditional: only applies in sunlight
 *   (current, ctx) -> ctx.inSunlight() ? current * 0.7f : current
 *
 *   // Override: replace base entirely
 *   (current, ctx) -> ctx.inSunlight() ? config.getSunlightDamageMultiplier() : current
 * </pre>
 */
@FunctionalInterface
public interface StatModifier extends com.epicseed.epiccore.modifier.ValueModifier<ModifierContext> {

    /**
     * Applies this modifier.
     *
     * @param current the accumulated value from previous modifiers
     * @param ctx     snapshot of player state at the moment of evaluation
     * @return the new value to pass to the next modifier
     */
    float apply(float current, ModifierContext ctx);
}
