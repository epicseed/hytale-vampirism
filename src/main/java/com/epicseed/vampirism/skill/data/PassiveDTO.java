package com.epicseed.vampirism.skill.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PassiveDTO {

    public String id;
    public String displayName;
    public String description;
    public String iconPath;
    public List<String> tags = Collections.emptyList();
    public List<Map<String, Object>> requirements = Collections.emptyList();
    public List<InlineModifierDTO> modifiers = Collections.emptyList();
    public List<Map<String, Object>> triggers = Collections.emptyList();
    public List<Map<String, Object>> actions = Collections.emptyList();
}
