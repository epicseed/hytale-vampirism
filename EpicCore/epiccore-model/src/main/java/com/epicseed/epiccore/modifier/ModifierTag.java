package com.epicseed.epiccore.modifier;

/**
 * Type-safe tag that identifies a modifier registration.
 */
public interface ModifierTag {

    String key();

    static ModifierTag of(String key) {
        return () -> key;
    }
}
