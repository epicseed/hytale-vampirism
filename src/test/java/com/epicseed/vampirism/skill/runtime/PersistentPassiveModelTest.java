package com.epicseed.vampirism.skill.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.epicseed.epiccore.skill.runtime.passive.PersistentEffectApplication;
import com.epicseed.epiccore.skill.runtime.passive.PersistentPassiveOwnerKey;
import com.epicseed.epiccore.skill.runtime.passive.PersistentPassiveState;

class PersistentPassiveModelTest {

    @Test
    void ownerKeySerializesAndParsesSkillAndPassiveKeys() {
        PersistentPassiveOwnerKey skill = PersistentPassiveOwnerKey.skill("BloodSucker");
        PersistentPassiveOwnerKey passive = PersistentPassiveOwnerKey.passive("NightVision");

        assertEquals("skill:BloodSucker", skill.serialized());
        assertEquals(skill, PersistentPassiveOwnerKey.parse("skill:BloodSucker"));
        assertEquals("passive:NightVision", passive.serialized());
        assertEquals(passive, PersistentPassiveOwnerKey.parse("passive:NightVision"));
    }

    @Test
    void ownerKeyParseRejectsMalformedValues() {
        assertNull(PersistentPassiveOwnerKey.parse(null));
        assertNull(PersistentPassiveOwnerKey.parse(""));
        assertNull(PersistentPassiveOwnerKey.parse("missing_separator"));
        assertNull(PersistentPassiveOwnerKey.parse(":missing_type"));
        assertNull(PersistentPassiveOwnerKey.parse("skill:"));
    }

    @Test
    void ownerKeyRequiresTypeAndId() {
        assertThrows(IllegalArgumentException.class, () -> new PersistentPassiveOwnerKey("", "id"));
        assertThrows(IllegalArgumentException.class, () -> new PersistentPassiveOwnerKey("skill", ""));
    }

    @Test
    void effectApplicationBuildsRemoveAction() {
        PersistentPassiveOwnerKey owner = PersistentPassiveOwnerKey.passive("NightVision");
        PersistentEffectApplication application = new PersistentEffectApplication(owner, "night_vision_effect", "self_target", 123L);

        Map<String, Object> action = application.toRemoveAction();

        assertEquals("removeEffect", action.get("type"));
        assertEquals("night_vision_effect", action.get("effectId"));
        assertEquals("self_target", action.get("targetingId"));
    }

    @Test
    void effectApplicationOmitsBlankTargetingId() {
        PersistentEffectApplication application = new PersistentEffectApplication(
                PersistentPassiveOwnerKey.skill("BatVision"), "night_vision_effect", "", 123L);

        Map<String, Object> action = application.toRemoveAction();

        assertFalse(action.containsKey("targetingId"));
    }

    @Test
    void effectApplicationRequiresEffectId() {
        assertThrows(IllegalArgumentException.class, () -> new PersistentEffectApplication(
                PersistentPassiveOwnerKey.skill("BatVision"), "", null, 123L));
    }

    @Test
    void passiveStateRecordsAndRemovesApplicationsByOwner() {
        PersistentPassiveState state = new PersistentPassiveState();
        PersistentPassiveOwnerKey firstOwner = PersistentPassiveOwnerKey.passive("NightVision");
        PersistentPassiveOwnerKey secondOwner = PersistentPassiveOwnerKey.skill("BatVision");
        PersistentEffectApplication firstApplication = new PersistentEffectApplication(firstOwner, "night_vision_effect", null, 100L);
        PersistentEffectApplication secondApplication = new PersistentEffectApplication(secondOwner, "bat_vision_effect", null, 200L);

        state.recordApply(firstOwner, 100L, List.of(firstApplication));
        state.recordApply(secondOwner, 200L, List.of(secondApplication));

        assertEquals(100L, state.lastApplyMs(firstOwner));
        assertEquals(200L, state.lastApplyMs(secondOwner));
        assertTrue(state.ownerKeys().contains(firstOwner.serialized()));
        assertTrue(state.ownerKeys().contains(secondOwner.serialized()));

        List<PersistentEffectApplication> removed = state.removeApplications(firstOwner.serialized());

        assertEquals(List.of(firstApplication), removed);
        assertFalse(state.ownerKeys().contains(firstOwner.serialized()));
        assertTrue(state.ownerKeys().contains(secondOwner.serialized()));
        assertFalse(state.isEmpty());

        state.removeApplications(secondOwner.serialized());
        assertTrue(state.isEmpty());
    }
}
