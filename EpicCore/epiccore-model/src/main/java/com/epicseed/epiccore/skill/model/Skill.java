package com.epicseed.epiccore.skill.model;

import com.epicseed.epiccore.skill.helpers.Position;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Skill {

    public String id;
    public String displayName;
    public String description;
    public boolean enabled = true;
    public int cost;
    public Position position;
    public String type;
    public String rarity;
    public String iconPath;
    public String overlayText;
    public String abilityId;
    public String passiveId;
    public List<String> tags = Collections.emptyList();
    public List<Skill> requires = Collections.emptyList();
    public List<InlineModifier> modifiers = Collections.emptyList();
    public List<Map<String, Object>> triggers = Collections.emptyList();
    public List<Map<String, Object>> actions = Collections.emptyList();
}
