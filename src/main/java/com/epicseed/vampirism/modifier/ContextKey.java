package com.epicseed.vampirism.modifier;

/**
 * Type-safe key for {@link ModifierContext#resolve(ContextKey, java.util.function.Supplier)}.
 *
 * <p>Each system that needs to cache a computed value declares a static final instance:
 * <pre>{@code
 * public static final ContextKey<Boolean> IN_SUNLIGHT = new ContextKey<>() {};
 * }</pre>
 *
 * <p>Equality is identity-based (default {@link Object#equals}), so each static final
 * instance is guaranteed unique — no string conflicts possible.
 *
 * @param <T> the type of the cached value
 */
public interface ContextKey<T> extends com.epicseed.epiccore.modifier.ContextKey<T> {
    // Marker interface — identity (reference) equality is intentional.
}
