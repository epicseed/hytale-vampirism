package com.epicseed.epiccore.skill.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InlineModifierDTO {

    public String modifierId;
    public String statId;
    public String operation;
    public float value;
    public Integer priority;
    public List<Map<String, Object>> conditions = Collections.emptyList();
    public Map<String, Object> target = Collections.emptyMap();
}
