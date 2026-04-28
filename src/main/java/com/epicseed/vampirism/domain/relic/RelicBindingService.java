package com.epicseed.vampirism.domain.relic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.registry.PlayerRelicBindings;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.runtime.AbilityCooldownTracker;
import com.epicseed.vampirism.skill.runtime.RelicBindings;
import com.epicseed.vampirism.systems.VampireInfectionSystem;

public final class RelicBindingService {

    public static final String[] SLOT_KEYS = { "primary", "secondary", "ability1", "ability2", "ability3" };

    private RelicBindingService() {
    }

    @Nonnull
    public static LinkedHashMap<String, String> getEffectiveBindings(@Nonnull UUID uuid) {
        Map<String, String> overrides = PlayerRelicBindings.get().bindingsFor(uuid);
        LinkedHashMap<String, String> effective = new LinkedHashMap<>();
        for (String slot : SLOT_KEYS) {
            String abilityId;
            if (overrides.containsKey(slot)) {
                abilityId = normalized(overrides.get(slot)).orElse(null);
            } else {
                abilityId = normalized(RelicBindings.abilityFor(slot)).orElse(null);
            }
            if (abilityId != null) {
                effective.put(slot, abilityId);
            }
        }
        return effective;
    }

    @Nonnull
    public static Optional<String> resolveAbilityForSlot(@Nonnull UUID uuid, @Nonnull String slot) {
        Map<String, String> overrides = PlayerRelicBindings.get().bindingsFor(uuid);
        if (overrides.containsKey(slot)) {
            return normalized(overrides.get(slot));
        }
        return normalized(RelicBindings.abilityFor(slot));
    }

    @Nonnull
    public static Optional<String> resolveActivationAbility(@Nonnull UUID uuid, @Nonnull String slot) {
        Optional<String> abilityId = resolveAbilityForSlot(uuid, slot);
        if (abilityId.isPresent()) {
            return abilityId;
        }
        if (PlayerSkillRegistry.get().isInfected(uuid)) {
            return Optional.of(VampireInfectionSystem.BLOOD_SUCKER_ABILITY_ID);
        }
        return Optional.empty();
    }

    public static void applyBindings(@Nonnull UUID uuid, @Nonnull Map<String, String> pending) {
        LinkedHashMap<String, String> bindingsToSave = new LinkedHashMap<>();
        for (String slot : SLOT_KEYS) {
            bindingsToSave.put(slot, normalized(pending.get(slot)).orElse(""));
        }
        PlayerRelicBindings.get().setAll(uuid, bindingsToSave);
    }

    @Nonnull
    public static List<Skill> listBindableAbilitySkills(@Nonnull UUID uuid) {
        SkillRegistry registry = Vampirism.getInstance().GetSkillRegistry();
        List<Skill> out = new ArrayList<>();
        for (Skill skill : registry.GetAll()) {
            if (skill.abilityId == null || skill.abilityId.isBlank()) {
                continue;
            }
            if (!PlayerSkillRegistry.get().hasSkill(uuid, skill.id)) {
                continue;
            }
            out.add(skill);
        }
        return out;
    }

    public static long remainingMs(@Nonnull UUID uuid, @Nonnull String abilityId) {
        return AbilityCooldownTracker.getRemainingMs(uuid, abilityId);
    }

    public static long slotBindingRemainingMs(@Nonnull UUID uuid,
                                              @Nonnull Map<String, String> savedState,
                                              @Nonnull String slot) {
        return normalized(savedState.get(slot))
                .map(abilityId -> AbilityCooldownTracker.getRemainingMs(uuid, abilityId))
                .orElse(0L);
    }

    public static boolean isAbilityOnCooldown(@Nonnull UUID uuid, @Nullable String abilityId) {
        return normalized(abilityId)
                .map(id -> AbilityCooldownTracker.getRemainingMs(uuid, id) > 0L)
                .orElse(false);
    }

    @Nullable
    public static String validatePendingBindings(@Nonnull UUID uuid,
                                                 @Nonnull Map<String, String> savedState,
                                                 @Nonnull Map<String, String> pending) {
        for (String slot : SLOT_KEYS) {
            String pendingAbilityId = normalized(pending.get(slot)).orElse(null);
            String savedAbilityId = normalized(savedState.get(slot)).orElse(null);
            if (java.util.Objects.equals(pendingAbilityId, savedAbilityId)) {
                continue;
            }
            if (slotBindingRemainingMs(uuid, savedState, slot) > 0L) {
                return slotCooldownMessage(uuid, savedState, slot);
            }
            if (pendingAbilityId != null && isAbilityOnCooldown(uuid, pendingAbilityId)) {
                return abilityCooldownMessage(uuid, pendingAbilityId);
            }
        }
        return null;
    }

    @Nonnull
    public static String slotCooldownMessage(@Nonnull UUID uuid,
                                             @Nonnull Map<String, String> savedState,
                                             @Nonnull String slot) {
        String abilityId = normalized(savedState.get(slot)).orElse(null);
        if (abilityId == null) {
            return "Slot '" + slotLabel(slot) + "' is locked by cooldown.";
        }
        long remainingMs = AbilityCooldownTracker.getRemainingMs(uuid, abilityId);
        return "Cannot change '" + slotLabel(slot) + "' while '" + abilityLabel(abilityId)
                + "' is on cooldown (" + formatCooldown(remainingMs) + "s remaining).";
    }

    @Nonnull
    public static String abilityCooldownMessage(@Nonnull UUID uuid, @Nonnull String abilityId) {
        long remainingMs = AbilityCooldownTracker.getRemainingMs(uuid, abilityId);
        return "Cannot rebind '" + abilityLabel(abilityId) + "' while it is on cooldown ("
                + formatCooldown(remainingMs) + "s remaining).";
    }

    @Nonnull
    public static String abilityLabel(@Nonnull String abilityId) {
        return abilityLabel(abilityId, findSkillByAbilityId(abilityId));
    }

    @Nonnull
    public static String abilityLabel(@Nonnull String abilityId, Skill owner) {
        Ability ability = Vampirism.getInstance().GetAbilityRegistry().Get(abilityId);
        return (ability != null && ability.displayName != null) ? ability.displayName
                : (owner != null ? owner.displayName : abilityId);
    }

    @Nonnull
    public static Optional<String> normalized(String abilityId) {
        return abilityId == null || abilityId.isBlank() ? Optional.empty() : Optional.of(abilityId);
    }

    @Nonnull
    public static String formatCooldown(long remainingMs) {
        return Long.toString(Math.max(1L, (long) Math.ceil(remainingMs / 1000.0)));
    }

    @Nonnull
    public static String slotLabel(@Nonnull String slot) {
        return switch (slot) {
            case "primary" -> "Primary";
            case "secondary" -> "Secondary";
            case "ability1" -> "Ability 1";
            case "ability2" -> "Ability 2";
            case "ability3" -> "Ability 3";
            default -> slot;
        };
    }

    private static Skill findSkillByAbilityId(@Nonnull String abilityId) {
        SkillRegistry registry = Vampirism.getInstance().GetSkillRegistry();
        for (Skill skill : registry.GetAll()) {
            if (abilityId.equals(skill.abilityId)) {
                return skill;
            }
        }
        return null;
    }
}
