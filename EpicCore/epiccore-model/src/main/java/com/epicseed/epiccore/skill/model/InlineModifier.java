package com.epicseed.epiccore.skill.model;

import com.epicseed.epiccore.modifier.StatType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class InlineModifier {

    public enum Operation { ADD, MULTIPLY, OVERRIDE }

    public String modifierId;
    public String statId;
    public StatType stat;
    public Operation operation = Operation.ADD;
    public float value;
    public int priority = 100;
    public List<Map<String, Object>> conditions = Collections.emptyList();
    public Map<String, Object> target = Collections.emptyMap();
}
