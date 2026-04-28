package com.epicseed.vampirism.skill.model;

import com.epicseed.vampirism.modifier.StatType;

import java.util.Collections;
import java.util.List;
import java.util.Map;

// Full mechanical definition of a modifier application, embedded directly on a tree node, passive, or effect.
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
