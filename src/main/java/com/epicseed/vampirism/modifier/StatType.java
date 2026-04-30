package com.epicseed.vampirism.modifier;

/**
 * Marker interface for all modifiable vampire attributes.
 *
 * <p>Concrete stat types are defined as enums in the domain that owns them.
 * The built-in vampire stats live in {@link VampireStatType}.
 * Future systems can define their own enums implementing this interface without
 * touching the central registry.
 */
public interface StatType extends com.epicseed.epiccore.modifier.StatType {}
