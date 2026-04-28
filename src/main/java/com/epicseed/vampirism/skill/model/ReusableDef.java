package com.epicseed.vampirism.skill.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ReusableDef {

    public String id;
    public String displayName;
    public String description;
    public List<String> tags = Collections.emptyList();
    public Map<String, Object> definition = Collections.emptyMap();

    public Map<String, Object> copyDefinition() {
        if (definition == null || definition.isEmpty()) return Collections.emptyMap();
        return new LinkedHashMap<>(definition);
    }
}
