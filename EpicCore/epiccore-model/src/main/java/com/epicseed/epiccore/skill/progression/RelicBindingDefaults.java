package com.epicseed.epiccore.skill.progression;

import java.util.List;

public interface RelicBindingDefaults {

    List<String> slotKeys();

    String defaultAbilityId(String slot);
}
