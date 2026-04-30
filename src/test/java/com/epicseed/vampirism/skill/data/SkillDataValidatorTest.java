package com.epicseed.vampirism.skill.data;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.epiccore.skill.data.AbilityDTO;
import com.epicseed.epiccore.skill.data.EffectDefDTO;
import com.epicseed.epiccore.skill.data.ReusableDefDTO;
import com.epicseed.epiccore.skill.data.SkillDTO;
import com.epicseed.epiccore.skill.data.SkillDataValidationResult;
import com.epicseed.epiccore.skill.data.SkillDataValidator;
import com.epicseed.epiccore.skill.data.SkillTreeDataTransfer;
import com.epicseed.epiccore.skill.data.StateEffectBindingDTO;
import com.epicseed.epiccore.skill.helpers.Position;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class SkillDataValidatorTest {

    private final SkillDataValidator validator = new SkillDataValidator();

    @Test
    void acceptsMinimalValidDataset() {
        SkillTreeDataTransfer data = minimalData();

        SkillDataValidationResult result = validator.validate(data);

        assertTrue(result.isValid(), () -> "Expected no issues, got: " + result.issues());
    }

    @Test
    void detectsDuplicateIdsWithinSection() {
        SkillTreeDataTransfer data = minimalData();
        data.tree.add(skill("root"));

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "tree", "root", "id", "duplicate");
    }

    @Test
    void detectsUnknownSkillAbilityReference() {
        SkillTreeDataTransfer data = minimalData();
        data.tree.get(0).abilityId = "missing_ability";

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "tree", "root", "abilityId", "unknown abilities id 'missing_ability'");
    }

    @Test
    void detectsUnknownSkillPassiveReference() {
        SkillTreeDataTransfer data = minimalData();
        data.tree.get(0).passiveId = "missing_passive";

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "tree", "root", "passiveId", "unknown passives id 'missing_passive'");
    }

    @Test
    void detectsUnknownStateRegistryStateReference() {
        SkillTreeDataTransfer data = minimalData();
        data.effects.add(effect("night_vision", "Potion_NightVision"));
        StateEffectBindingDTO binding = new StateEffectBindingDTO();
        binding.stateId = "MISSING_STATE";
        binding.effectId = "Potion_NightVision";
        data.stateRegistry.add(binding);

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "stateRegistry", "MISSING_STATE", "stateId", "unknown states id 'MISSING_STATE'");
    }

    @Test
    void detectsUnknownRelicBindingAbilityReference() {
        SkillTreeDataTransfer data = minimalData();
        data.relicBindings.put("primary", "missing_ability");

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "relicBindings", "primary", "abilityId", "unknown abilities id 'missing_ability'");
    }

    @Test
    void detectsReusableDefinitionWithoutType() {
        SkillTreeDataTransfer data = minimalData();
        ReusableDefDTO action = reusable("bad_action", Map.of("effectId", "some_effect"));
        data.actions.add(action);

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "actions", "bad_action", "definition.type", "is required");
    }

    @Test
    void detectsNegativeAbilityRanges() {
        SkillTreeDataTransfer data = minimalData();
        AbilityDTO ability = ability("bad_ability");
        ability.cooldown = -1f;
        ability.bloodCost = -5;
        data.abilities.add(ability);

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "abilities", "bad_ability", "cooldown", "must be non-negative");
        assertIssue(result, "abilities", "bad_ability", "bloodCost", "must be non-negative");
    }

    @Test
    void detectsAbilityActionsWithoutTargeting() {
        SkillTreeDataTransfer data = minimalData();
        AbilityDTO ability = ability("bad_ability");
        ability.actions = List.of(Map.of("type", "sendMessage"));
        data.abilities.add(ability);

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "abilities", "bad_ability", "targeting", "is required when ability has actions");
    }

    @Test
    void detectsEffectActionWithoutEffectId() {
        SkillTreeDataTransfer data = minimalData();
        data.actions.add(reusable("bad_apply", Map.of("type", "applyEffect")));

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "actions", "bad_apply", "definition.effectId", "is required");
    }

    @Test
    void detectsActivateAbilityActionWithoutAbilityId() {
        SkillTreeDataTransfer data = minimalData();
        data.actions.add(reusable("bad_activate", Map.of("type", "activateAbility")));

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "actions", "bad_activate", "definition.abilityId", "is required");
    }

    @Test
    void detectsStateConditionWithoutStateId() {
        SkillTreeDataTransfer data = minimalData();
        data.conditions.add(reusable("bad_condition", Map.of("type", "state")));

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "conditions", "bad_condition", "definition.stateId", "is required");
    }

    @Test
    void detectsConditionRequirementWithoutConditionId() {
        SkillTreeDataTransfer data = minimalData();
        data.requirements.add(reusable("bad_requirement", Map.of("type", "condition")));

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "requirements", "bad_requirement", "definition.conditionId", "is required");
    }

    @Test
    void detectsOnConditionTriggerWithoutCondition() {
        SkillTreeDataTransfer data = minimalData();
        data.triggers.add(reusable("bad_trigger", Map.of("type", "onCondition")));

        SkillDataValidationResult result = validator.validate(data);

        assertIssue(result, "triggers", "bad_trigger", "definition.conditionId", "is required for onCondition trigger");
    }

    private static SkillTreeDataTransfer minimalData() {
        SkillTreeDataTransfer data = new SkillTreeDataTransfer();
        data.tree = new ArrayList<>(List.of(skill("root")));
        data.passives = new ArrayList<>();
        data.abilities = new ArrayList<>();
        data.modifiers = new ArrayList<>();
        data.effects = new ArrayList<>();
        data.states = new ArrayList<>();
        data.stats = new ArrayList<>();
        data.conditions = new ArrayList<>();
        data.requirements = new ArrayList<>();
        data.triggers = new ArrayList<>();
        data.actions = new ArrayList<>();
        data.targetings = new ArrayList<>();
        data.stateRegistry = new ArrayList<>();
        data.relicBindings = new LinkedHashMap<>();
        return data;
    }

    private static SkillDTO skill(String id) {
        SkillDTO dto = new SkillDTO();
        dto.id = id;
        dto.position = new Position();
        return dto;
    }

    private static AbilityDTO ability(String id) {
        AbilityDTO dto = new AbilityDTO();
        dto.id = id;
        return dto;
    }

    private static EffectDefDTO effect(String id, String effectId) {
        EffectDefDTO dto = new EffectDefDTO();
        dto.id = id;
        dto.effectId = effectId;
        return dto;
    }

    private static ReusableDefDTO reusable(String id, Map<String, Object> definition) {
        ReusableDefDTO dto = new ReusableDefDTO();
        dto.id = id;
        dto.definition = definition;
        return dto;
    }

    private static void assertIssue(SkillDataValidationResult result,
                                    String section,
                                    String owner,
                                    String field,
                                    String messagePart) {
        assertFalse(result.isValid(), "Expected validation to fail");
        assertTrue(result.issues().stream().anyMatch(issue ->
                        issue.section().equals(section)
                                && issue.owner().equals(owner)
                                && issue.field().equals(field)
                                && issue.message().contains(messagePart)),
                () -> "Missing issue " + section + "[" + owner + "] " + field
                        + " containing '" + messagePart + "'. Actual: " + result.issues());
    }
}
