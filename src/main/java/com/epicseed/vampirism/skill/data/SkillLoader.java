package com.epicseed.vampirism.skill.data;

import com.epicseed.vampirism.modifier.StatType;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.EffectDef;
import com.epicseed.vampirism.skill.model.InlineModifier;
import com.epicseed.vampirism.skill.model.ModifierDef;
import com.epicseed.vampirism.skill.model.Passive;
import com.epicseed.vampirism.skill.model.ReusableDef;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.model.StatDef;
import com.epicseed.vampirism.skill.model.VampireState;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.ModifierDefRegistry;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.ReusableDefRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.registry.StateRegistry;
import com.epicseed.vampirism.skill.registry.StatDefRegistry;
import com.epicseed.vampirism.skill.runtime.RelicBindings;
import com.epicseed.vampirism.skill.runtime.StateEffectBindings;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Reads split skill data JSON files with Jackson and populates all registries.
public class SkillLoader {

    private final ObjectMapper mapper = new ObjectMapper();

    private static final String DATA_DIR = "data/vampirism/skills";
    private static final String LEGACY_DATA_DIR = "Common/UI/Custom/Vampirism/Data/SkillsData";
    private static final String LEGACY_JSON_PATH = "Common/UI/Custom/Vampirism/Data/SkillsData.json";

    // -------------------------------------------------------------------------
    // Tree (skill graph)
    // -------------------------------------------------------------------------

    /**
     * Loads all tree nodes from the split skill-data files, registers them into the provided registry,
     * resolves cross-skill requirements, and validates the graph for cycles.
     */
    public List<Skill> LoadSkills(SkillRegistry registry) {
        SkillTreeDataTransfer data = readJson();

        List<Skill> skillList = new ArrayList<>();
        for (SkillDTO dto : data.tree) {
            Skill skill = toSkill(dto);
            skillList.add(skill);
            registry.Register(skill);
        }

        for (SkillDTO dto : data.tree) {
            resolveRequirements(dto, registry.GetSkill(dto.id), registry);
        }

        detectCycles(skillList);
        return skillList;
    }

    public void ValidateSkillData() {
        readJson();
    }

    // -------------------------------------------------------------------------
    // Definitions (passives, abilities, modifiers, effects, states)
    // -------------------------------------------------------------------------

    /**
     * Loads all definition sections from the split skill-data files into their respective registries.
     * Call after {@link #LoadSkills} — the two phases are intentionally separate.
     */
    public void LoadDefinitions(PassiveRegistry passiveRegistry,
                                AbilityRegistry abilityRegistry,
                                ModifierDefRegistry modifierDefRegistry,
                                EffectDefRegistry effectDefRegistry,
                                StateRegistry stateRegistry,
                                StatDefRegistry statDefRegistry,
                                ReusableDefRegistry conditionRegistry,
                                ReusableDefRegistry requirementRegistry,
                                ReusableDefRegistry triggerRegistry,
                                ReusableDefRegistry actionRegistry,
                                ReusableDefRegistry targetingRegistry) {
        SkillTreeDataTransfer data = readJson();

        for (ModifierDefDTO dto : data.modifiers) {
            modifierDefRegistry.Register(toModifierDef(dto));
        }

        for (EffectDefDTO dto : data.effects) {
            effectDefRegistry.Register(toEffectDef(dto));
        }

        for (StateDTO dto : data.states) {
            stateRegistry.Register(toState(dto));
        }

        for (StatDefDTO dto : data.stats) {
            statDefRegistry.Register(toStatDef(dto));
        }

        for (PassiveDTO dto : data.passives) {
            passiveRegistry.Register(toPassive(dto));
        }

        for (AbilityDTO dto : data.abilities) {
            abilityRegistry.Register(toAbility(dto));
        }

        for (ReusableDefDTO dto : data.conditions) {
            conditionRegistry.Register(toReusableDef(dto));
        }

        for (ReusableDefDTO dto : data.requirements) {
            requirementRegistry.Register(toReusableDef(dto));
        }

        for (ReusableDefDTO dto : data.triggers) {
            triggerRegistry.Register(toReusableDef(dto));
        }

        for (ReusableDefDTO dto : data.actions) {
            actionRegistry.Register(toReusableDef(dto));
        }

        for (ReusableDefDTO dto : data.targetings) {
            targetingRegistry.Register(toReusableDef(dto));
        }

        // State registry: state-id -> Hytale effect-id mapping consumed by SkillRuntimeStateResolver.
        Map<String, String> stateMap = new LinkedHashMap<>();
        for (StateEffectBindingDTO dto : data.stateRegistry) {
            if (dto.stateId == null || dto.stateId.isBlank()) continue;
            if (dto.effectId == null || dto.effectId.isBlank()) continue;
            stateMap.put(dto.stateId, dto.effectId);
        }
        StateEffectBindings.set(stateMap);

        // Relic bindings: slot-key -> ability-id used by VampirismRelicCommand.
        Map<String, String> relicMap = data.relicBindings != null ? data.relicBindings : Collections.emptyMap();
        RelicBindings.set(relicMap);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private Skill toSkill(SkillDTO dto) {
        Skill skill = new Skill();
        skill.id          = dto.id;
        skill.displayName = dto.displayName != null ? dto.displayName : dto.id;
        skill.description = dto.description;
        skill.enabled     = dto.enabled == null || dto.enabled;
        skill.cost        = dto.cost;
        skill.position    = dto.position;
        skill.type        = dto.type;
        skill.rarity      = dto.rarity != null ? dto.rarity : "common";
        skill.iconPath    = dto.iconPath;
        skill.overlayText = dto.overlayText;
        skill.abilityId   = dto.abilityId;
        skill.passiveId   = dto.passiveId;
        skill.tags        = copyStrings(dto.tags);
        skill.modifiers   = toInlineModifiers(dto.modifiers, 100);
        skill.triggers    = copyObjectList(dto.triggers);
        skill.actions     = copyObjectList(dto.actions);
        return skill;
    }

    private Passive toPassive(PassiveDTO dto) {
        Passive passive = new Passive();
        passive.id           = dto.id;
        passive.displayName  = dto.displayName != null ? dto.displayName : dto.id;
        passive.description  = dto.description;
        passive.iconPath     = dto.iconPath;
        passive.tags         = copyStrings(dto.tags);
        passive.requirements = copyObjectList(dto.requirements);
        passive.modifiers    = toInlineModifiers(dto.modifiers, 100);
        passive.triggers     = copyObjectList(dto.triggers);
        passive.actions      = copyObjectList(dto.actions);
        return passive;
    }

    private Ability toAbility(AbilityDTO dto) {
        Ability ability = new Ability();
        ability.id              = dto.id;
        ability.displayName     = dto.displayName != null ? dto.displayName : dto.id;
        ability.description     = dto.description;
        ability.iconPath        = dto.iconPath;
        ability.tags            = copyStrings(dto.tags);
        ability.cooldown        = dto.cooldown;
        ability.duration        = dto.duration;
        ability.bloodCost       = dto.bloodCost;
        ability.castTime        = dto.castTime;
        ability.charges         = dto.charges;
        ability.channelDuration = dto.channelDuration;
        ability.effects         = copyStrings(dto.effects);
        ability.requirements    = copyObjectList(dto.requirements);
        ability.targeting       = copyObjectMap(dto.targeting);
        ability.actions         = copyObjectList(dto.actions);
        return ability;
    }

    private ModifierDef toModifierDef(ModifierDefDTO dto) {
        ModifierDef mod = new ModifierDef();
        mod.id          = dto.id;
        mod.displayName = dto.displayName;
        mod.description = dto.description;
        return mod;
    }

    private EffectDef toEffectDef(EffectDefDTO dto) {
        EffectDef effect = new EffectDef();
        effect.id           = dto.id;
        effect.displayName  = dto.displayName;
        effect.description  = dto.description;
        effect.effectId     = dto.effectId;
        effect.duration     = dto.duration;
        effect.tags         = copyStrings(dto.tags);
        effect.requirements = copyObjectList(dto.requirements);
        effect.modifiers    = toInlineModifiers(dto.modifiers, 200);
        effect.actions      = copyObjectList(dto.actions);
        return effect;
    }

    private VampireState toState(StateDTO dto) {
        VampireState state = new VampireState();
        state.id          = dto.id;
        state.displayName = dto.displayName;
        state.description = dto.description;
        return state;
    }

    private StatDef toStatDef(StatDefDTO dto) {
        StatDef stat = new StatDef();
        stat.id          = dto.id;
        stat.displayName = dto.displayName;
        stat.description = dto.description;
        return stat;
    }

    private ReusableDef toReusableDef(ReusableDefDTO dto) {
        ReusableDef def = new ReusableDef();
        def.id          = dto.id;
        def.displayName = dto.displayName;
        def.description = dto.description;
        def.tags        = copyStrings(dto.tags);
        def.definition  = copyObjectMap(dto.definition);
        return def;
    }

    // -------------------------------------------------------------------------
    // Inline modifier helpers
    // -------------------------------------------------------------------------

    /**
     * Maps a list of DTOs to InlineModifier instances, applying {@code defaultPriority}
     * when the DTO omits it. Callers should pass the priority floor appropriate for their
     * context (100 for skill/passive modifiers, 200 for active-effect modifiers).
     */
    private List<InlineModifier> toInlineModifiers(List<InlineModifierDTO> dtos, int defaultPriority) {
        if (dtos == null || dtos.isEmpty()) return Collections.emptyList();
        List<InlineModifier> result = new ArrayList<>();
        for (InlineModifierDTO dto : dtos) {
            InlineModifier mod = toInlineModifier(dto, defaultPriority);
            if (mod != null) result.add(mod);
        }
        return result;
    }

    private InlineModifier toInlineModifier(InlineModifierDTO dto, int defaultPriority) {
        if (dto == null || dto.statId == null || dto.statId.isBlank()) return null;

        InlineModifier.Operation operation;
        try {
            String raw = dto.operation != null ? dto.operation.strip().toUpperCase() : "ADD";
            if ("SET".equals(raw)) raw = "OVERRIDE";
            operation = InlineModifier.Operation.valueOf(raw);
        } catch (IllegalArgumentException e) {
            operation = InlineModifier.Operation.ADD;
        }

        InlineModifier mod = new InlineModifier();
        mod.modifierId = dto.modifierId;
        mod.statId     = dto.statId;
        mod.stat       = resolveStatType(dto.statId);
        mod.operation  = operation;
        mod.value      = dto.value;
        mod.priority   = (dto.priority != null && dto.priority >= 0) ? dto.priority : defaultPriority;
        mod.conditions = copyObjectList(dto.conditions);
        mod.target     = copyObjectMap(dto.target);
        return mod;
    }

    private StatType resolveStatType(String statId) {
        if (statId == null || statId.isBlank()) return null;
        try {
            return VampireStatType.valueOf(statId.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void resolveRequirements(SkillDTO dto, Skill skill, SkillRegistry registry) {
        List<Skill> requires = new ArrayList<>();
        for (String reqId : dto.requires) {
            Skill req = registry.GetSkill(reqId);
            if (req == null) throw new RuntimeException(
                    "Skill '" + dto.id + "' requires unknown skill: " + reqId);
            requires.add(req);
        }
        skill.requires = requires;
    }

    // -------------------------------------------------------------------------
    // Copy helpers
    // -------------------------------------------------------------------------

    private List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(values);
    }

    private List<Map<String, Object>> copyObjectList(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> copy = new ArrayList<>(values.size());
        for (Map<String, Object> value : values) {
            copy.add(copyObjectMap(value));
        }
        return copy;
    }

    private Map<String, Object> copyObjectMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) return Collections.emptyMap();
        return new LinkedHashMap<>(value);
    }

    // -------------------------------------------------------------------------
    // Cycle detection (DFS)
    // -------------------------------------------------------------------------

    private void detectCycles(List<Skill> skills) {
        Set<String> visited = new HashSet<>();
        Set<String> stack   = new HashSet<>();
        for (Skill skill : skills) {
            if (!visited.contains(skill.id)) {
                dfs(skill, visited, stack, skill.id);
            }
        }
    }

    private void dfs(Skill skill, Set<String> visited, Set<String> stack, String path) {
        visited.add(skill.id);
        stack.add(skill.id);
        for (Skill req : skill.requires) {
            if (stack.contains(req.id)) {
                throw new RuntimeException("Cyclic skill dependency detected: " + path + " -> " + req.id);
            }
            if (!visited.contains(req.id)) {
                dfs(req, visited, stack, path + " -> " + req.id);
            }
        }
        stack.remove(skill.id);
    }

    // -------------------------------------------------------------------------
    // JSON parsing
    // -------------------------------------------------------------------------

    private SkillTreeDataTransfer readJson() {
        String splitDataDir = splitDataDir();
        SkillTreeDataTransfer data = splitDataDir != null
                ? readSplitJson(splitDataDir)
                : readLegacyJson();
        new SkillDataValidator().validateOrThrow(data);
        return data;
    }

    private SkillTreeDataTransfer readSplitJson(String dataDir) {
        SkillTreeDataTransfer data = new SkillTreeDataTransfer();
        data.tree = readList(sectionPath(dataDir, "tree.json"), SkillDTO[].class);
        data.passives = readList(sectionPath(dataDir, "passives.json"), PassiveDTO[].class);
        data.abilities = readList(sectionPath(dataDir, "abilities.json"), AbilityDTO[].class);
        data.modifiers = readList(sectionPath(dataDir, "modifiers.json"), ModifierDefDTO[].class);
        data.effects = readList(sectionPath(dataDir, "effects.json"), EffectDefDTO[].class);
        data.states = readList(sectionPath(dataDir, "states.json"), StateDTO[].class);
        data.stats = readList(sectionPath(dataDir, "stats.json"), StatDefDTO[].class);
        data.conditions = readList(sectionPath(dataDir, "conditions.json"), ReusableDefDTO[].class);
        data.requirements = readList(sectionPath(dataDir, "requirements.json"), ReusableDefDTO[].class);
        data.triggers = readList(sectionPath(dataDir, "triggers.json"), ReusableDefDTO[].class);
        data.actions = readList(sectionPath(dataDir, "actions.json"), ReusableDefDTO[].class);
        data.targetings = readList(sectionPath(dataDir, "targetings.json"), ReusableDefDTO[].class);
        data.stateRegistry = readList(sectionPath(dataDir, "stateRegistry.json"), StateEffectBindingDTO[].class);
        data.relicBindings = readStringMap(sectionPath(dataDir, "relicBindings.json"));
        return data;
    }

    private SkillTreeDataTransfer readLegacyJson() {
        InputStream json = getClass().getClassLoader().getResourceAsStream(LEGACY_JSON_PATH);
        if (json == null) {
            throw new RuntimeException("Skill data not found at: " + DATA_DIR + ", " + LEGACY_DATA_DIR + " or " + LEGACY_JSON_PATH);
        }
        try {
            return mapper.readValue(json, SkillTreeDataTransfer.class);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing legacy SkillsData.json", e);
        }
    }

    private boolean resourceExists(String path) {
        return getClass().getClassLoader().getResource(path) != null;
    }

    private String splitDataDir() {
        if (resourceExists(sectionPath(DATA_DIR, "tree.json"))) {
            return DATA_DIR;
        }
        if (resourceExists(sectionPath(LEGACY_DATA_DIR, "tree.json"))) {
            return LEGACY_DATA_DIR;
        }
        return null;
    }

    private String sectionPath(String dataDir, String fileName) {
        return dataDir + "/" + fileName;
    }

    private <T> List<T> readList(String path, Class<T[]> arrayClass) {
        InputStream json = getClass().getClassLoader().getResourceAsStream(path);
        if (json == null) {
            throw new RuntimeException("Skill data section not found at: " + path);
        }
        try {
            T[] values = mapper.readValue(json, arrayClass);
            if (values == null || values.length == 0) {
                return Collections.emptyList();
            }
            List<T> list = new ArrayList<>(values.length);
            Collections.addAll(list, values);
            return list;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing skill data section: " + path, e);
        }
    }

    private Map<String, String> readStringMap(String path) {
        InputStream json = getClass().getClassLoader().getResourceAsStream(path);
        if (json == null) {
            throw new RuntimeException("Skill data section not found at: " + path);
        }
        try {
            Map<String, String> values = mapper.readValue(json, mapper.getTypeFactory()
                    .constructMapType(LinkedHashMap.class, String.class, String.class));
            return values != null ? values : Collections.emptyMap();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing skill data section: " + path, e);
        }
    }
}
