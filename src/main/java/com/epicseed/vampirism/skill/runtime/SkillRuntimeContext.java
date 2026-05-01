package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.skill.runtime.HytaleAbilityRuntimeContext;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SkillRuntimeContext implements HytaleAbilityRuntimeContext<SkillRuntimeContext> {

    private final UUID uuid;
    private final Ref<EntityStore> ref;
    private final Ref<EntityStore> targetRef;
    private final Store<EntityStore> store;
    private final List<String> activationPath;
    private final String currentAbilityId;
    private final String currentEffectId;
    private final String currentPassiveId;
    private final String currentSkillId;

    public SkillRuntimeContext(@Nullable UUID uuid,
                               @Nonnull Ref<EntityStore> ref,
                               @Nonnull Store<EntityStore> store) {
        this(uuid, ref, null, store, List.of(), null, null, null, null);
    }

    public SkillRuntimeContext(@Nullable UUID uuid,
                               @Nonnull Ref<EntityStore> ref,
                               @Nullable Ref<EntityStore> targetRef,
                               @Nonnull Store<EntityStore> store) {
        this(uuid, ref, targetRef, store, List.of(), null, null, null, null);
    }

    private SkillRuntimeContext(@Nullable UUID uuid,
                                @Nonnull Ref<EntityStore> ref,
                                @Nullable Ref<EntityStore> targetRef,
                                @Nonnull Store<EntityStore> store,
                                @Nonnull List<String> activationPath,
                                @Nullable String currentAbilityId,
                                @Nullable String currentEffectId,
                                @Nullable String currentPassiveId,
                                @Nullable String currentSkillId) {
        this.uuid = uuid;
        this.ref = ref;
        this.targetRef = targetRef;
        this.store = store;
        this.activationPath = activationPath;
        this.currentAbilityId = currentAbilityId;
        this.currentEffectId = currentEffectId;
        this.currentPassiveId = currentPassiveId;
        this.currentSkillId = currentSkillId;
    }

    @Nullable
    public UUID uuid() { return uuid; }

    @Nonnull
    public Ref<EntityStore> ref() { return ref; }

    @Nullable
    public Ref<EntityStore> targetRef() { return targetRef; }

    @Nonnull
    public Store<EntityStore> store() { return store; }

    @Nullable
    public String currentAbilityId() { return currentAbilityId; }

    @Nullable
    public String currentEffectId() { return currentEffectId; }

    @Nullable
    public String currentPassiveId() { return currentPassiveId; }

    @Nullable
    public String currentSkillId() { return currentSkillId; }

    public ModifierContext modifierContext() {
        return new ModifierContext(uuid, ref, store, currentAbilityId, currentEffectId, currentPassiveId, currentSkillId);
    }

    public SkillRuntimeContext withTarget(@Nullable Ref<EntityStore> newTargetRef) {
        return new SkillRuntimeContext(
                uuid, ref, newTargetRef, store, activationPath,
                currentAbilityId, currentEffectId, currentPassiveId, currentSkillId);
    }

    public int activationDepth() {
        return activationPath.size();
    }

    public boolean hasAbilityInActivationPath(@Nonnull String abilityId) {
        return activationPath.contains(abilityId);
    }

    @Nonnull
    public String activationPathString() {
        return String.join(" -> ", activationPath);
    }

    public SkillRuntimeContext withActivatedAbility(@Nonnull String abilityId) {
        ArrayList<String> nextPath = new ArrayList<>(activationPath.size() + 1);
        nextPath.addAll(activationPath);
        nextPath.add(abilityId);
        return new SkillRuntimeContext(
                uuid, ref, targetRef, store, List.copyOf(nextPath),
                abilityId, currentEffectId, currentPassiveId, currentSkillId);
    }

    public SkillRuntimeContext withEffectScope(@Nullable String effectId) {
        return new SkillRuntimeContext(
                uuid, ref, targetRef, store, activationPath,
                currentAbilityId, effectId, currentPassiveId, currentSkillId);
    }

    public SkillRuntimeContext withPassiveScope(@Nullable String passiveId) {
        return new SkillRuntimeContext(
                uuid, ref, targetRef, store, activationPath,
                currentAbilityId, currentEffectId, passiveId, currentSkillId);
    }

    public SkillRuntimeContext withSkillScope(@Nullable String skillId) {
        return new SkillRuntimeContext(
                uuid, ref, targetRef, store, activationPath,
                currentAbilityId, currentEffectId, currentPassiveId, skillId);
    }
}
