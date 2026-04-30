package com.epicseed.epiccore.skill.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EffectDef {

    public String id;
    public String displayName;
    public String description;
    public String effectId;
    public float duration;
    public List<String> tags = Collections.emptyList();
    public List<Map<String, Object>> requirements = Collections.emptyList();
    public List<InlineModifier> modifiers = Collections.emptyList();
    public List<Map<String, Object>> actions = Collections.emptyList();
}
