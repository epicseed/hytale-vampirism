package com.epicseed.vampirism.modifier;

/**
 * Vampirism facade over the shared EpicCore modifier registry.
 */
public final class ModifierRegistry extends com.epicseed.epiccore.modifier.ModifierRegistry<ModifierContext> {

    private static ModifierRegistry instance;

    private ModifierRegistry() {}

    public static ModifierRegistry get() {
        if (instance == null) instance = new ModifierRegistry();
        return instance;
    }
}
