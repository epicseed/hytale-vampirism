package com.epicseed.epiccore.skill.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ReusableDefDTO {

    public String id;
    public String displayName;
    public String description;
    public List<String> tags = Collections.emptyList();
    public Map<String, Object> definition = Collections.emptyMap();
}
