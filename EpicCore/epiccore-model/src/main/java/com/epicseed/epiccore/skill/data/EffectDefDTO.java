package com.epicseed.epiccore.skill.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EffectDefDTO {

    public String id;
    public String displayName;
    public String description;
    public String effectId;
    public float duration;
    public List<String> tags = Collections.emptyList();
    public List<Map<String, Object>> requirements = Collections.emptyList();
    public List<InlineModifierDTO> modifiers = Collections.emptyList();
    public List<Map<String, Object>> actions = Collections.emptyList();
}
