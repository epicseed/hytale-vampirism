package com.epicseed.epiccore.skill.data;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.epiccore.skill.helpers.Position;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.InlineModifier;
import com.epicseed.epiccore.skill.model.ModifierDef;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.model.StateDef;

import com.fasterxml.jackson.databind.ObjectMapper;

public class SkillDataLoader<A extends Ability,
        M extends ModifierDef,
        R extends ReusableDef,
        S extends StateDef,
        T extends com.epicseed.epiccore.skill.model.StatDef> {

    private final ObjectMapper mapper = new ObjectMapper();
    private final SkillDataPaths dataPaths;
    private final SkillLoaderModelAdapter<A, M, R, S, T> modelAdapter;
    private final SkillDataLoadHooks loadHooks;

    public SkillDataLoader(SkillDataPaths dataPaths,
                           SkillLoaderModelAdapter<A, M, R, S, T> modelAdapter,
                           SkillDataLoadHooks loadHooks) {
        this.dataPaths = Objects.requireNonNull(dataPaths, "dataPaths");
        this.modelAdapter = Objects.requireNonNull(modelAdapter, "modelAdapter");
        this.loadHooks = loadHooks != null ? loadHooks : SkillDataLoadHooks.noop();
    }

    public List<Skill> loadSkills(IdRegistry<Skill> registry) {
        SkillTreeDataTransfer data = readJson();

        List<Skill> skillList = new ArrayList<>();
        for (SkillDTO dto : data.tree) {
            Skill skill = toSkill(dto);
            skillList.add(skill);
            registry.register(skill);
        }

        for (SkillDTO dto : data.tree) {
            resolveRequirements(dto, registry.get(dto.id), registry);
        }

        detectCycles(skillList);
        return skillList;
    }

    public void validateSkillData() {
        readJson();
    }

    public void loadDefinitions(SkillDefinitionRegistries<A, M, R, S, T> registries) {
        SkillTreeDataTransfer data = readJson();

        for (ModifierDefDTO dto : data.modifiers) {
            registries.modifierDefRegistry().register(toModifierDef(dto));
        }

        for (EffectDefDTO dto : data.effects) {
            registries.effectDefRegistry().register(toEffectDef(dto));
        }

        for (StateDTO dto : data.states) {
            registries.stateRegistry().register(toState(dto));
        }

        for (StatDefDTO dto : data.stats) {
            registries.statDefRegistry().register(toStatDef(dto));
        }

        for (PassiveDTO dto : data.passives) {
            registries.passiveRegistry().register(toPassive(dto));
        }

        for (AbilityDTO dto : data.abilities) {
            registries.abilityRegistry().register(toAbility(dto));
        }

        for (ReusableDefDTO dto : data.conditions) {
            registries.conditionRegistry().register(toReusableDef(dto));
        }

        for (ReusableDefDTO dto : data.requirements) {
            registries.requirementRegistry().register(toReusableDef(dto));
        }

        for (ReusableDefDTO dto : data.triggers) {
            registries.triggerRegistry().register(toReusableDef(dto));
        }

        for (ReusableDefDTO dto : data.actions) {
            registries.actionRegistry().register(toReusableDef(dto));
        }

        for (ReusableDefDTO dto : data.targetings) {
            registries.targetingRegistry().register(toReusableDef(dto));
        }

        Map<String, String> stateMap = new LinkedHashMap<>();
        for (StateEffectBindingDTO dto : data.stateRegistry) {
            if (dto.stateId == null || dto.stateId.isBlank()) {
                continue;
            }
            if (dto.effectId == null || dto.effectId.isBlank()) {
                continue;
            }
            stateMap.put(dto.stateId, dto.effectId);
        }
        loadHooks.applyStateEffectBindings(stateMap);

        Map<String, String> abilitySlotMap = data.relicBindings != null ? data.relicBindings : Collections.emptyMap();
        loadHooks.applyAbilitySlotBindings(abilitySlotMap);
    }

    protected SkillTreeDataTransfer readJson() {
        String splitDataDir = splitDataDir();
        SkillTreeDataTransfer data = splitDataDir != null
                ? readSplitJson(splitDataDir)
                : readLegacyJson();
        new SkillDataValidator().validateOrThrow(data);
        return data;
    }

    private Skill toSkill(SkillDTO dto) {
        Skill skill = new Skill();
        skill.id = dto.id;
        skill.displayName = dto.displayName != null ? dto.displayName : dto.id;
        skill.description = dto.description;
        skill.enabled = dto.enabled == null || dto.enabled;
        skill.cost = dto.cost;
        skill.position = copyPosition(dto.position);
        skill.type = dto.type;
        skill.rarity = dto.rarity != null ? dto.rarity : "common";
        skill.iconPath = dto.iconPath;
        skill.overlayText = dto.overlayText;
        skill.abilityId = dto.abilityId;
        skill.passiveId = dto.passiveId;
        skill.tags = copyStrings(dto.tags);
        skill.modifiers = toInlineModifiers(dto.modifiers, 100);
        skill.triggers = copyObjectList(dto.triggers);
        skill.actions = copyObjectList(dto.actions);
        return skill;
    }

    private Position copyPosition(Position position) {
        if (position == null) {
            return null;
        }
        Position copy = new Position();
        copy.x = position.x;
        copy.y = position.y;
        return copy;
    }

    private Passive toPassive(PassiveDTO dto) {
        Passive passive = new Passive();
        passive.id = dto.id;
        passive.displayName = dto.displayName != null ? dto.displayName : dto.id;
        passive.description = dto.description;
        passive.iconPath = dto.iconPath;
        passive.tags = copyStrings(dto.tags);
        passive.requirements = copyObjectList(dto.requirements);
        passive.modifiers = toInlineModifiers(dto.modifiers, 100);
        passive.triggers = copyObjectList(dto.triggers);
        passive.actions = copyObjectList(dto.actions);
        return passive;
    }

    private A toAbility(AbilityDTO dto) {
        A ability = modelAdapter.newAbility();
        ability.id = dto.id;
        ability.displayName = dto.displayName != null ? dto.displayName : dto.id;
        ability.description = dto.description;
        ability.iconPath = dto.iconPath;
        ability.tags = copyStrings(dto.tags);
        ability.cooldown = dto.cooldown;
        ability.duration = dto.duration;
        ability.bloodCost = dto.bloodCost;
        ability.castTime = dto.castTime;
        ability.charges = dto.charges;
        ability.channelDuration = dto.channelDuration;
        ability.effects = copyStrings(dto.effects);
        ability.requirements = copyObjectList(dto.requirements);
        ability.targeting = copyObjectMap(dto.targeting);
        ability.actions = copyObjectList(dto.actions);
        return ability;
    }

    private M toModifierDef(ModifierDefDTO dto) {
        M mod = modelAdapter.newModifierDef();
        mod.id = dto.id;
        mod.displayName = dto.displayName;
        mod.description = dto.description;
        return mod;
    }

    private EffectDef toEffectDef(EffectDefDTO dto) {
        EffectDef effect = new EffectDef();
        effect.id = dto.id;
        effect.displayName = dto.displayName;
        effect.description = dto.description;
        effect.effectId = dto.effectId;
        effect.duration = dto.duration;
        effect.tags = copyStrings(dto.tags);
        effect.requirements = copyObjectList(dto.requirements);
        effect.modifiers = toInlineModifiers(dto.modifiers, 200);
        effect.actions = copyObjectList(dto.actions);
        return effect;
    }

    private S toState(StateDTO dto) {
        S state = modelAdapter.newState();
        state.id = dto.id;
        state.displayName = dto.displayName;
        state.description = dto.description;
        return state;
    }

    private T toStatDef(StatDefDTO dto) {
        T stat = modelAdapter.newStatDef();
        stat.id = dto.id;
        stat.displayName = dto.displayName;
        stat.description = dto.description;
        return stat;
    }

    private R toReusableDef(ReusableDefDTO dto) {
        R def = modelAdapter.newReusableDef();
        def.id = dto.id;
        def.displayName = dto.displayName;
        def.description = dto.description;
        def.tags = copyStrings(dto.tags);
        def.definition = copyObjectMap(dto.definition);
        return def;
    }

    private List<InlineModifier> toInlineModifiers(List<InlineModifierDTO> dtos, int defaultPriority) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<InlineModifier> result = new ArrayList<>();
        for (InlineModifierDTO dto : dtos) {
            InlineModifier mod = toInlineModifier(dto, defaultPriority);
            if (mod != null) {
                result.add(mod);
            }
        }
        return result;
    }

    private InlineModifier toInlineModifier(InlineModifierDTO dto, int defaultPriority) {
        if (dto == null || dto.statId == null || dto.statId.isBlank()) {
            return null;
        }

        InlineModifier.Operation operation;
        try {
            String raw = dto.operation != null ? dto.operation.strip().toUpperCase() : "ADD";
            if ("SET".equals(raw)) {
                raw = "OVERRIDE";
            }
            operation = InlineModifier.Operation.valueOf(raw);
        } catch (IllegalArgumentException e) {
            operation = InlineModifier.Operation.ADD;
        }

        InlineModifier mod = new InlineModifier();
        mod.modifierId = dto.modifierId;
        mod.statId = dto.statId;
        mod.stat = modelAdapter.resolveStatType(dto.statId);
        mod.operation = operation;
        mod.value = dto.value;
        mod.priority = (dto.priority != null && dto.priority >= 0) ? dto.priority : defaultPriority;
        mod.conditions = copyObjectList(dto.conditions);
        mod.target = copyObjectMap(dto.target);
        return mod;
    }

    private void resolveRequirements(SkillDTO dto, Skill skill, IdRegistry<Skill> registry) {
        List<Skill> requires = new ArrayList<>();
        for (String reqId : dto.requires) {
            Skill req = registry.get(reqId);
            if (req == null) {
                throw new RuntimeException("Skill '" + dto.id + "' requires unknown skill: " + reqId);
            }
            requires.add(req);
        }
        skill.requires = requires;
    }

    private List<String> copyStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<>(values);
    }

    private List<Map<String, Object>> copyObjectList(List<Map<String, Object>> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> copy = new ArrayList<>(values.size());
        for (Map<String, Object> value : values) {
            copy.add(copyObjectMap(value));
        }
        return copy;
    }

    private Map<String, Object> copyObjectMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>(value);
    }

    private void detectCycles(List<Skill> skills) {
        Set<String> visited = new HashSet<>();
        Set<String> stack = new HashSet<>();
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
        InputStream json = getClass().getClassLoader().getResourceAsStream(dataPaths.legacyJsonPath());
        if (json == null) {
            throw new RuntimeException("Skill data not found at: "
                    + dataPaths.primaryDataDir() + ", "
                    + dataPaths.fallbackDataDir() + " or "
                    + dataPaths.legacyJsonPath());
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
        if (resourceExists(sectionPath(dataPaths.primaryDataDir(), "tree.json"))) {
            return dataPaths.primaryDataDir();
        }
        if (resourceExists(sectionPath(dataPaths.fallbackDataDir(), "tree.json"))) {
            return dataPaths.fallbackDataDir();
        }
        return null;
    }

    private String sectionPath(String dataDir, String fileName) {
        return dataDir + "/" + fileName;
    }

    private <V> List<V> readList(String path, Class<V[]> arrayClass) {
        InputStream json = getClass().getClassLoader().getResourceAsStream(path);
        if (json == null) {
            throw new RuntimeException("Skill data section not found at: " + path);
        }
        try {
            V[] values = mapper.readValue(json, arrayClass);
            if (values == null || values.length == 0) {
                return Collections.emptyList();
            }
            List<V> list = new ArrayList<>(values.length);
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
