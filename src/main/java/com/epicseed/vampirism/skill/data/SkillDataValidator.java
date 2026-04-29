package com.epicseed.vampirism.skill.data;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class SkillDataValidator {

    public SkillDataValidationResult validate(SkillTreeDataTransfer data) {
        SkillDataValidationResult result = new SkillDataValidationResult();
        if (data == null) {
            result.add("root", "SkillsData", "data", "skill data is null");
            return result;
        }

        SkillDataIndex index = buildIndex(data, result);
        validateSkills(data.tree, index, result);
        validatePassives(data.passives, index, result);
        validateAbilities(data.abilities, index, result);
        validateEffects(data.effects, index, result);
        validateReusableDefinitions("conditions", data.conditions, index, result);
        validateReusableDefinitions("requirements", data.requirements, index, result);
        validateReusableDefinitions("triggers", data.triggers, index, result);
        validateReusableDefinitions("actions", data.actions, index, result);
        validateReusableDefinitions("targetings", data.targetings, index, result);
        validateStateRegistry(data.stateRegistry, index, result);
        validateRelicBindings(data.relicBindings, index, result);

        return result;
    }

    public void validateOrThrow(SkillTreeDataTransfer data) {
        validate(data).throwIfInvalid();
    }

    private SkillDataIndex buildIndex(SkillTreeDataTransfer data, SkillDataValidationResult result) {
        return new SkillDataIndex(
                collectIds("tree", safeList(data.tree), dto -> dto.id, result),
                collectIds("passives", safeList(data.passives), dto -> dto.id, result),
                collectIds("abilities", safeList(data.abilities), dto -> dto.id, result),
                collectIds("modifiers", safeList(data.modifiers), dto -> dto.id, result),
                collectIds("effects", safeList(data.effects), dto -> dto.id, result),
                collectEffectAssetIds(safeList(data.effects), result),
                collectIds("states", safeList(data.states), dto -> dto.id, result),
                collectIds("stats", safeList(data.stats), dto -> dto.id, result),
                collectIds("conditions", safeList(data.conditions), dto -> dto.id, result),
                collectIds("requirements", safeList(data.requirements), dto -> dto.id, result),
                collectIds("triggers", safeList(data.triggers), dto -> dto.id, result),
                collectIds("actions", safeList(data.actions), dto -> dto.id, result),
                collectIds("targetings", safeList(data.targetings), dto -> dto.id, result)
        );
    }

    private void validateSkills(List<SkillDTO> skills,
                                SkillDataIndex index,
                                SkillDataValidationResult result) {
        int i = 0;
        for (SkillDTO dto : safeList(skills)) {
            String owner = owner("skill", dto != null ? dto.id : null, i++);
            if (dto == null) {
                result.add("tree", owner, "entry", "skill entry is null");
                continue;
            }

            if (dto.cost < 0) {
                result.add("tree", owner, "cost", "must be non-negative");
            }
            if (dto.position == null) {
                result.add("tree", owner, "position", "is required for skill tree layout");
            }
            validateOptionalRef("tree", owner, "abilityId", dto.abilityId, index.abilityIds, "abilities", result);
            validateOptionalRef("tree", owner, "passiveId", dto.passiveId, index.passiveIds, "passives", result);
            for (String requiredSkill : safeList(dto.requires)) {
                validateRequiredRef("tree", owner, "requires", requiredSkill, index.skillIds, "tree", result);
            }
            validateInlineModifiers("tree", owner, dto.modifiers, index, result);
            validateObjectRefs("tree", owner, "triggers", dto.triggers, index, result);
            validateObjectRefs("tree", owner, "actions", dto.actions, index, result);
        }
    }

    private void validatePassives(List<PassiveDTO> passives,
                                  SkillDataIndex index,
                                  SkillDataValidationResult result) {
        int i = 0;
        for (PassiveDTO dto : safeList(passives)) {
            String owner = owner("passive", dto != null ? dto.id : null, i++);
            if (dto == null) {
                result.add("passives", owner, "entry", "passive entry is null");
                continue;
            }
            validateObjectRefs("passives", owner, "requirements", dto.requirements, index, result);
            validateInlineModifiers("passives", owner, dto.modifiers, index, result);
            validateObjectRefs("passives", owner, "triggers", dto.triggers, index, result);
            validateObjectRefs("passives", owner, "actions", dto.actions, index, result);
        }
    }

    private void validateAbilities(List<AbilityDTO> abilities,
                                   SkillDataIndex index,
                                   SkillDataValidationResult result) {
        int i = 0;
        for (AbilityDTO dto : safeList(abilities)) {
            String owner = owner("ability", dto != null ? dto.id : null, i++);
            if (dto == null) {
                result.add("abilities", owner, "entry", "ability entry is null");
                continue;
            }
            validateNonNegative("abilities", owner, "cooldown", dto.cooldown, result);
            validateNonNegative("abilities", owner, "duration", dto.duration, result);
            validateNonNegative("abilities", owner, "bloodCost", dto.bloodCost, result);
            validateNonNegative("abilities", owner, "castTime", dto.castTime, result);
            validateNonNegative("abilities", owner, "charges", dto.charges, result);
            validateNonNegative("abilities", owner, "channelDuration", dto.channelDuration, result);
            for (String effectId : safeList(dto.effects)) {
                validateRequiredRef("abilities", owner, "effects", effectId, index.effectIds, "effects", result);
            }
            validateObjectRefs("abilities", owner, "requirements", dto.requirements, index, result);
            validateObjectRefs("abilities", owner, "targeting", dto.targeting, index, result);
            validateObjectRefs("abilities", owner, "actions", dto.actions, index, result);
        }
    }

    private void validateEffects(List<EffectDefDTO> effects,
                                 SkillDataIndex index,
                                 SkillDataValidationResult result) {
        int i = 0;
        for (EffectDefDTO dto : safeList(effects)) {
            String owner = owner("effect", dto != null ? dto.id : null, i++);
            if (dto == null) {
                result.add("effects", owner, "entry", "effect entry is null");
                continue;
            }
            validateRequiredText("effects", owner, "effectId", dto.effectId, result);
            validateObjectRefs("effects", owner, "requirements", dto.requirements, index, result);
            validateInlineModifiers("effects", owner, dto.modifiers, index, result);
            validateObjectRefs("effects", owner, "actions", dto.actions, index, result);
        }
    }

    private void validateReusableDefinitions(String section,
                                             List<ReusableDefDTO> definitions,
                                             SkillDataIndex index,
                                             SkillDataValidationResult result) {
        int i = 0;
        for (ReusableDefDTO dto : safeList(definitions)) {
            String owner = owner(section.substring(0, section.length() - 1), dto != null ? dto.id : null, i++);
            if (dto == null) {
                result.add(section, owner, "entry", "definition entry is null");
                continue;
            }
            if (dto.definition == null || dto.definition.isEmpty()) {
                result.add(section, owner, "definition", "must not be empty");
                continue;
            }
            if (isBlank(asString(dto.definition.get("type")))) {
                result.add(section, owner, "definition.type", "is required");
            }
            validateObjectRefs(section, owner, "definition", dto.definition, index, result);
        }
    }

    private void validateStateRegistry(List<StateEffectBindingDTO> bindings,
                                       SkillDataIndex index,
                                       SkillDataValidationResult result) {
        int i = 0;
        for (StateEffectBindingDTO dto : safeList(bindings)) {
            String owner = owner("stateRegistry", dto != null ? dto.stateId : null, i++);
            if (dto == null) {
                result.add("stateRegistry", owner, "entry", "binding entry is null");
                continue;
            }
            validateRequiredRef("stateRegistry", owner, "stateId", dto.stateId, index.stateIds, "states", result);
            validateRequiredRef("stateRegistry", owner, "effectId", dto.effectId, index.effectAssetIds, "effect asset ids", result);
        }
    }

    private void validateRelicBindings(Map<String, String> bindings,
                                       SkillDataIndex index,
                                       SkillDataValidationResult result) {
        if (bindings == null) return;
        for (Map.Entry<String, String> entry : bindings.entrySet()) {
            String slot = entry.getKey();
            String owner = isBlank(slot) ? "<blank slot>" : slot;
            validateRequiredText("relicBindings", owner, "slot", slot, result);
            validateRequiredRef("relicBindings", owner, "abilityId", entry.getValue(), index.abilityIds, "abilities", result);
        }
    }

    private void validateInlineModifiers(String section,
                                         String owner,
                                         List<InlineModifierDTO> modifiers,
                                         SkillDataIndex index,
                                         SkillDataValidationResult result) {
        int i = 0;
        for (InlineModifierDTO dto : safeList(modifiers)) {
            String fieldPrefix = "modifiers[" + i++ + "]";
            if (dto == null) {
                result.add(section, owner, fieldPrefix, "modifier entry is null");
                continue;
            }
            validateRequiredRef(section, owner, fieldPrefix + ".modifierId", dto.modifierId, index.modifierIds, "modifiers", result);
            validateRequiredRef(section, owner, fieldPrefix + ".statId", dto.statId, index.statIds, "stats", result);
            if (dto.priority != null && dto.priority < 0) {
                result.add(section, owner, fieldPrefix + ".priority", "must be non-negative when present");
            }
            validateObjectRefs(section, owner, fieldPrefix + ".conditions", dto.conditions, index, result);
            validateObjectRefs(section, owner, fieldPrefix + ".target", dto.target, index, result);
        }
    }

    @SuppressWarnings("unchecked")
    private void validateObjectRefs(String section,
                                    String owner,
                                    String field,
                                    Object value,
                                    SkillDataIndex index,
                                    SkillDataValidationResult result) {
        if (value == null) return;

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = asString(entry.getKey());
                String path = field + "." + key;
                Object rawValue = entry.getValue();
                if (rawValue instanceof String stringValue) {
                    validateMapReference(section, owner, path, key, stringValue, index, result);
                }
                if (rawValue instanceof Map<?, ?> || rawValue instanceof List<?>) {
                    validateObjectRefs(section, owner, path, rawValue, index, result);
                }
            }
            return;
        }

        if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                Object element = list.get(i);
                if (element == null) {
                    result.add(section, owner, field + "[" + i + "]", "entry is null");
                    continue;
                }
                validateObjectRefs(section, owner, field + "[" + i + "]", element, index, result);
            }
        }
    }

    private void validateMapReference(String section,
                                      String owner,
                                      String field,
                                      String key,
                                      String value,
                                      SkillDataIndex index,
                                      SkillDataValidationResult result) {
        if ("actionId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.actionIds, "actions", result);
        } else if ("conditionId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.conditionIds, "conditions", result);
        } else if ("requirementId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.requirementIds, "requirements", result);
        } else if ("triggerId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.triggerIds, "triggers", result);
        } else if ("targetingId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.targetingIds, "targetings", result);
        } else if ("effectId".equals(key) || key.endsWith("EffectId")) {
            validateRequiredRef(section, owner, field, value, index.effectIds, "effects", result);
        } else if ("stateId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.stateIds, "states", result);
        } else if ("statId".equals(key) || key.endsWith("StatId")) {
            validateRequiredRef(section, owner, field, value, index.statIds, "stats", result);
        } else if ("modifierId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.modifierIds, "modifiers", result);
        } else if ("abilityId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.abilityIds, "abilities", result);
        } else if ("passiveId".equals(key)) {
            validateRequiredRef(section, owner, field, value, index.passiveIds, "passives", result);
        }
    }

    private <T> Set<String> collectIds(String section,
                                       List<T> entries,
                                       Function<T, String> idGetter,
                                       SkillDataValidationResult result) {
        Set<String> ids = new HashSet<>();
        int i = 0;
        for (T entry : entries) {
            String owner = owner(section, null, i++);
            if (entry == null) {
                result.add(section, owner, "id", "entry is null");
                continue;
            }
            String id = trimToNull(idGetter.apply(entry));
            if (id == null) {
                result.add(section, owner, "id", "is required");
                continue;
            }
            if (!ids.add(id)) {
                result.add(section, id, "id", "duplicate id in " + section);
            }
        }
        return ids;
    }

    private Set<String> collectEffectAssetIds(List<EffectDefDTO> effects,
                                              SkillDataValidationResult result) {
        Set<String> ids = new HashSet<>();
        int i = 0;
        for (EffectDefDTO effect : effects) {
            String owner = owner("effect", effect != null ? effect.id : null, i++);
            if (effect == null) continue;
            String effectId = trimToNull(effect.effectId);
            if (effectId == null) {
                result.add("effects", owner, "effectId", "is required");
                continue;
            }
            ids.add(effectId);
        }
        return ids;
    }

    private void validateOptionalRef(String section,
                                     String owner,
                                     String field,
                                     String value,
                                     Set<String> ids,
                                     String targetSection,
                                     SkillDataValidationResult result) {
        if (isBlank(value)) return;
        validateRequiredRef(section, owner, field, value, ids, targetSection, result);
    }

    private void validateRequiredRef(String section,
                                     String owner,
                                     String field,
                                     String value,
                                     Set<String> ids,
                                     String targetSection,
                                     SkillDataValidationResult result) {
        String id = trimToNull(value);
        if (id == null) {
            result.add(section, owner, field, "is required");
            return;
        }
        if (!ids.contains(id)) {
            result.add(section, owner, field, "unknown " + targetSection + " id '" + id + "'");
        }
    }

    private void validateRequiredText(String section,
                                      String owner,
                                      String field,
                                      String value,
                                      SkillDataValidationResult result) {
        if (isBlank(value)) {
            result.add(section, owner, field, "is required");
        }
    }

    private void validateNonNegative(String section,
                                     String owner,
                                     String field,
                                     float value,
                                     SkillDataValidationResult result) {
        if (value < 0) {
            result.add(section, owner, field, "must be non-negative");
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : Collections.emptyList();
    }

    private String owner(String section, String id, int index) {
        String normalizedId = trimToNull(id);
        return normalizedId != null ? normalizedId : section + "#" + index;
    }

    private String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean isBlank(String value) {
        return trimToNull(value) == null;
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private record SkillDataIndex(Set<String> skillIds,
                                  Set<String> passiveIds,
                                  Set<String> abilityIds,
                                  Set<String> modifierIds,
                                  Set<String> effectIds,
                                  Set<String> effectAssetIds,
                                  Set<String> stateIds,
                                  Set<String> statIds,
                                  Set<String> conditionIds,
                                  Set<String> requirementIds,
                                  Set<String> triggerIds,
                                  Set<String> actionIds,
                                  Set<String> targetingIds) {
    }
}
