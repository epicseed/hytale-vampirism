package com.epicseed.epiccore.skill.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class AbilityDTO {

    public String id;
    public String displayName;
    public String description;
    public String iconPath;
    public List<String> tags = Collections.emptyList();
    public float cooldown;
    public float duration;
    public int bloodCost;
    public float castTime;
    public int charges;
    public float channelDuration;
    public List<String> effects = Collections.emptyList();
    public List<Map<String, Object>> requirements = Collections.emptyList();
    public Map<String, Object> targeting = Collections.emptyMap();
    public List<Map<String, Object>> actions = Collections.emptyList();
}
