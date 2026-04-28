package com.epicseed.vampirism.skill.data;

import java.util.Collections;
import java.util.Map;

public class StatDefDTO {

    public String id;
    public String displayName;
    public String description;
    public String category;
    public String unit;
    public String status;
    public Object baseValue;
    public Map<String, Object> binding = Collections.emptyMap();
    public String notes;
}
