package com.epicseed.epiccore.skill.runtime;

import com.epicseed.epiccore.skill.model.ReusableDef;

public interface ReusableDefinitionProvider {

    ReusableDef get(String kind, String id);
}
