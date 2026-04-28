package com.epicseed.vampirism.modifier;

/**
 * Type-safe tag that identifies a modifier registration.
 *
 * <p>Static/enum tags: each system declares an enum implementing this interface:
 * <pre>{@code
 * public enum SunburnTag implements ModifierTag {
 *     SUNLIGHT;
 *     public String key() { return name(); }
 * }
 * }</pre>
 *
 * <p>Dynamic tags (skills, effects, relics): use {@link #of(String)} to wrap a
 * runtime string without losing type safety at the registration call-site:
 * <pre>{@code
 * ModifierTag.of("skill:" + skill.id)
 * ModifierTag.of("effect:holiness")
 * }</pre>
 *
 * <p>{@link ModifierRegistry#unregisterByTagPrefix} compares via {@link #key()},
 * so dynamic tags created from the same string are treated as equal.
 */
public interface ModifierTag {

    /** Unique string key for this tag. Used for prefix-based bulk unregistration. */
    String key();

    /** Creates a {@link ModifierTag} from a plain string (for dynamic/runtime tags). */
    static ModifierTag of(String key) {
        return () -> key;
    }
}
