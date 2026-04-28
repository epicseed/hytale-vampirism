package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data-driven registry mapping state ids to Hytale effect ids for "effect-backed" states.
 *
 * <p>Populated at boot by {@link com.epicseed.vampirism.skill.data.SkillLoader} from
 * {@code stateRegistry.json}. Consulted by
 * {@link SkillRuntimeStateResolver} to answer {@code isStateActive(stateId, ctx)} for any
 * stateId that maps to a persistent {@code EntityEffect}.
 *
 * <p>States whose value requires runtime computation (e.g. {@code IS_NIGHT}, {@code IS_SNEAKING},
 * {@code IN_SUNLIGHT}, {@code IS_STARVING}, {@code IS_OVERFED}, {@code IS_BLOOD_STATE_NORMAL},
 * {@code IS_IN_BAT_FORM}, and aliases such as {@code IS_IN_FRENZY}) stay code-resolved in
 * {@link SkillRuntimeStateResolver}.
 */
public final class StateEffectBindings {

    private static volatile Map<String, String> bindings = Collections.emptyMap();

    private StateEffectBindings() {}

    /** Replace the current binding map (called once at plugin boot by the loader). */
    public static synchronized void set(@Nonnull Map<String, String> newBindings) {
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(newBindings));
    }

    /** Returns the Hytale effect id mapped to {@code stateId}, or {@code null} if unknown. */
    @Nullable
    public static String effectIdFor(@Nonnull String stateId) {
        return bindings.get(stateId);
    }

    @Nonnull
    public static Map<String, String> all() {
        return bindings;
    }
}
