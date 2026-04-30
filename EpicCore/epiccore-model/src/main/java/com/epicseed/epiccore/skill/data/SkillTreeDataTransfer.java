package com.epicseed.epiccore.skill.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SkillTreeDataTransfer {

    public List<SkillDTO> tree = Collections.emptyList();
    public List<PassiveDTO> passives = Collections.emptyList();
    public List<AbilityDTO> abilities = Collections.emptyList();
    public List<ModifierDefDTO> modifiers = Collections.emptyList();
    public List<EffectDefDTO> effects = Collections.emptyList();
    public List<StateDTO> states = Collections.emptyList();
    public List<StatDefDTO> stats = Collections.emptyList();
    public List<ReusableDefDTO> conditions = Collections.emptyList();
    public List<ReusableDefDTO> requirements = Collections.emptyList();
    public List<ReusableDefDTO> triggers = Collections.emptyList();
    public List<ReusableDefDTO> actions = Collections.emptyList();
    public List<ReusableDefDTO> targetings = Collections.emptyList();
    public List<StateEffectBindingDTO> stateRegistry = Collections.emptyList();
    public Map<String, String> relicBindings = Collections.emptyMap();
}
