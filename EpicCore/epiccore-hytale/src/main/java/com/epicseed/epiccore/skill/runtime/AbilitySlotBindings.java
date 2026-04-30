package com.epicseed.epiccore.skill.runtime;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class AbilitySlotBindings {

    private static volatile Map<String, String> bindings = Collections.emptyMap();

    private AbilitySlotBindings() {}

    public static synchronized void set(Map<String, String> newBindings) {
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(newBindings));
    }

    public static String abilityFor(String slotKey) {
        return bindings.get(slotKey);
    }

    public static Map<String, String> all() {
        return bindings;
    }
}
