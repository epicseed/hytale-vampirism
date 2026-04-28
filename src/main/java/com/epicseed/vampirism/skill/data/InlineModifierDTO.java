package com.epicseed.vampirism.skill.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

// Represents a modifier application embedded inside a tree node, passive, or effect.
public class InlineModifierDTO {

    public String modifierId;
    public String statId;
    public String operation;  // ADD | MULTIPLY | OVERRIDE — defaults to ADD if absent
    public float value;
    public Integer priority;
    public List<Map<String, Object>> conditions = Collections.emptyList();
    public Map<String, Object> target = Collections.emptyMap();
}
