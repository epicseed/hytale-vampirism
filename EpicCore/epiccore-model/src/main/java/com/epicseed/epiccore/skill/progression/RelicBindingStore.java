package com.epicseed.epiccore.skill.progression;

import java.util.Map;
import java.util.UUID;

public interface RelicBindingStore {

    Map<String, String> bindingsFor(UUID uuid, int presetIndex);

    int activePresetIndex(UUID uuid);

    void setActivePreset(UUID uuid, int presetIndex);

    void setAll(UUID uuid, int presetIndex, Map<String, String> bindings);

    void setAll(UUID uuid,
                Map<Integer, ? extends Map<String, String>> presetBindings,
                int activePresetIndex);
}
