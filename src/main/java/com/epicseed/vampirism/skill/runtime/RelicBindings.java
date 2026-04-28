package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data-driven registry mapping relic slot keys to ability ids.
 *
 * <p>Populated at boot by {@link com.epicseed.vampirism.skill.data.SkillLoader} from
 * {@code relicBindings.json}. Consulted by
 * {@link com.epicseed.vampirism.commands.VampirismRelicCommand} to resolve which ability a
 * given relic action (e.g. {@code primary}) should activate.
 */
public final class RelicBindings {

    private static volatile Map<String, String> bindings = Collections.emptyMap();

    private RelicBindings() {}

    public static synchronized void set(@Nonnull Map<String, String> newBindings) {
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(newBindings));
    }

    @Nullable
    public static String abilityFor(@Nonnull String slotKey) {
        return bindings.get(slotKey);
    }

    @Nonnull
    public static Map<String, String> all() {
        return bindings;
    }
}
