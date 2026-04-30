package com.epicseed.epiccore.skill.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class StateEffectBindings {

    private static volatile Map<String, String> bindings = Collections.emptyMap();

    private StateEffectBindings() {}

    public static synchronized void set(Map<String, String> newBindings) {
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(newBindings));
    }

    public static String effectIdFor(String stateId) {
        return bindings.get(stateId);
    }

    public static Map<String, String> all() {
        return bindings;
    }
}
