package com.epicseed.vampirism.skill.data;

import com.epicseed.vampirism.skill.helpers.Position;

import java.util.Collections;
import java.util.List;
import java.util.Map;

// Holds the crude data from JSON.
// HARDCODED since changes to skills require updates to this class and Skill.
public class SkillDTO {

    public String id;
    public String displayName;
    public String description;
    public Boolean enabled;
    public int cost;
    public Position position;
    public String type;
    public String rarity;
    public String iconPath;
    public String overlayText;
    public String abilityId;
    public String passiveId;
    public List<String> tags = Collections.emptyList();
    public List<String> requires = Collections.emptyList();
    public List<InlineModifierDTO> modifiers = Collections.emptyList();
    public List<Map<String, Object>> triggers = Collections.emptyList();
    public List<Map<String, Object>> actions = Collections.emptyList();
}
