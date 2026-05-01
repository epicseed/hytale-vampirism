package com.epicseed.vampirism.skill.runtime;
import com.epicseed.vampirism.modifier.ModifierContext;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.AbilityAccessProvider;
import com.epicseed.epiccore.skill.runtime.AbilityActivationCharge;
import com.epicseed.epiccore.skill.runtime.AbilityDefinitionProvider;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions;
import com.epicseed.epiccore.skill.runtime.AbilityRuntimeKernel;
import com.epicseed.epiccore.skill.runtime.ResolvedTargets;
import com.epicseed.epiccore.skill.runtime.SkillActivationResult;
import com.epicseed.epiccore.skill.runtime.TargetingResolver;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public final class AbilityService {

    private static final AbilityDefinitionProvider UNINITIALIZED_DEFINITION_PROVIDER = new AbilityDefinitionProvider() {
        @Override
        public Ability getAbility(String id) {
            return null;
        }

        @Override
        public Skill getSkill(String id) {
            return null;
        }

        @Override
        public EffectDef getEffect(String id) {
            return null;
        }
    };
    private static final AbilityAccessProvider UNINITIALIZED_ACCESS_PROVIDER = new AbilityAccessProvider() {
        @Override
        public boolean allowsTemporaryAbility(UUID uuid, String abilityId) {
            return false;
        }

        @Override
        public Set<String> getUnlockedSkillIds(UUID uuid) {
            return Set.of();
        }
    };
    private static final AbilityResourcePort UNINITIALIZED_RESOURCE_PORT = new AbilityResourcePort() {
        @Override
        public boolean canAffordBlood(Ref<EntityStore> casterRef, int bloodCost) {
            return false;
        }

        @Override
        public void spendBlood(Ref<EntityStore> casterRef, int bloodCost) {
        }
    };

    private static AbilityDefinitionProvider definitionProvider = UNINITIALIZED_DEFINITION_PROVIDER;
    private static AbilityAccessProvider accessProvider = UNINITIALIZED_ACCESS_PROVIDER;
    private static AbilityResourcePort resourcePort = UNINITIALIZED_RESOURCE_PORT;
    private static BiConsumer<SkillRuntimeContext, String> activationTriggerDispatcher = (ctx, abilityId) -> {};

    private static final AbilityRuntimeKernel<SkillRuntimeContext, Ref<EntityStore>> KERNEL =
            new AbilityRuntimeKernel<>(
                    new AbilityDefinitionProvider() {
                        @Override
                        public Ability getAbility(String id) {
                            return definitionProvider.getAbility(id);
                        }

                        @Override
                        public Skill getSkill(String id) {
                            return definitionProvider.getSkill(id);
                        }

                        @Override
                        public EffectDef getEffect(String id) {
                            return definitionProvider.getEffect(id);
                        }
                    },
                    new AbilityAccessProvider() {
                        @Override
                        public boolean allowsTemporaryAbility(UUID uuid, String abilityId) {
                            return accessProvider.allowsTemporaryAbility(uuid, abilityId);
                        }

                        @Override
                        public Set<String> getUnlockedSkillIds(UUID uuid) {
                            return accessProvider.getUnlockedSkillIds(uuid);
                        }
                    },
                    SkillRequirementEvaluator::evaluateAll,
                    AbilityService::resolveTargets,
                    SkillActionExecutor.sharedExecutor(),
                    new com.epicseed.epiccore.skill.runtime.AbilityResourcePort<>() {
                        @Override
                        public boolean canAfford(Ref<EntityStore> target, int resourceCost) {
                            return resourcePort.canAffordBlood(target, resourceCost);
                        }

                        @Override
                        public void spend(Ref<EntityStore> target, int resourceCost) {
                            resourcePort.spendBlood(target, resourceCost);
                        }
                    },
                    AbilityService::resolveCharge,
                    (ctx, abilityId) -> activationTriggerDispatcher.accept(ctx, abilityId));

    private AbilityService() {
    }

    public static void init(AbilityDefinitionProvider definitionProvider,
                            AbilityAccessProvider accessProvider,
                            AbilityResourcePort resourcePort,
                            BiConsumer<SkillRuntimeContext, String> activationTriggerDispatcher) {
        AbilityService.definitionProvider = definitionProvider != null
                ? definitionProvider
                : UNINITIALIZED_DEFINITION_PROVIDER;
        AbilityService.accessProvider = accessProvider != null
                ? accessProvider
                : UNINITIALIZED_ACCESS_PROVIDER;
        AbilityService.resourcePort = resourcePort != null
                ? resourcePort
                : UNINITIALIZED_RESOURCE_PORT;
        AbilityService.activationTriggerDispatcher = activationTriggerDispatcher != null
                ? activationTriggerDispatcher
                : (ctx, abilityId) -> {};
    }

    @Nonnull
    public static SkillActivationResult activate(@Nonnull String abilityId,
                                                 @Nonnull UUID uuid,
                                                 @Nonnull Ref<EntityStore> casterRef,
                                                 @Nullable Ref<EntityStore> targetRef,
                                                 @Nonnull Store<EntityStore> store) {
        return KERNEL.activate(abilityId, new SkillRuntimeContext(uuid, casterRef, targetRef, store));
    }

    @Nonnull
    public static SkillActivationResult activateFromAction(@Nonnull String abilityId,
                                                           @Nonnull SkillRuntimeContext ctx) {
        return KERNEL.activateFromAction(abilityId, ctx);
    }

    @Nonnull
    static SkillActivationResult activate(@Nonnull String abilityId, @Nonnull SkillRuntimeContext ctx) {
        return KERNEL.activate(abilityId, ctx);
    }

    @Nonnull
    private static AbilityActivationCharge resolveCharge(@Nonnull Ability ability,
                                                         @Nonnull SkillRuntimeContext ctx) {
        if (isToggleOffActivation(ability, ctx)) {
            return new AbilityActivationCharge(0L, 0);
        }
        return new AbilityActivationCharge(computeCooldownMs(ability, ctx), computeResourceCost(ability, ctx));
    }

    private static long computeCooldownMs(@Nonnull Ability ability,
                                          @Nonnull SkillRuntimeContext ctx) {
        float overrideSeconds = ModifierContext.REGISTRY.compute(
                VampireStatType.ABILITY_COOLDOWN_OVERRIDE_SECONDS, 0f, ctx.modifierContext());
        if (overrideSeconds > 0f) {
            return Math.max(0L, (long) (overrideSeconds * 1000L));
        }
        if (ability.cooldown <= 0f) {
            return 0L;
        }
        float reduction = ModifierContext.REGISTRY.compute(
                VampireStatType.ABILITY_COOLDOWN_REDUCTION, 0f, ctx.modifierContext());
        float effectiveCooldown = ability.cooldown * Math.max(0.1f, 1f - reduction);
        return Math.max(0L, (long) (effectiveCooldown * 1000L));
    }

    private static int computeResourceCost(@Nonnull Ability ability,
                                        @Nonnull SkillRuntimeContext ctx) {
        if (ability.resourceCost <= 0) {
            return 0;
        }
        float multiplier = ModifierContext.REGISTRY.compute(
                VampireStatType.ABILITY_BLOOD_COST_MULTIPLIER, 1f, ctx.modifierContext());
        return Math.max(0, Math.round(ability.resourceCost * Math.max(0f, multiplier)));
    }

    @Nonnull
    private static ResolvedTargets<Ref<EntityStore>> resolveTargets(@Nullable Map<String, Object> targeting,
                                                                    @Nonnull SkillRuntimeContext ctx) {
        return TargetingResolver.resolve(targeting, ctx);
    }

    private static boolean isToggleOffActivation(@Nonnull Ability ability, @Nonnull SkillRuntimeContext ctx) {
        if (ability.actions == null || ability.actions.isEmpty()) {
            return false;
        }

        Ref<EntityStore> targetRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();
        boolean foundToggle = false;
        for (Map<String, Object> actionSpec : ability.actions) {
            Map<String, Object> action = SkillRuntimeDefinitions.resolveAction(actionSpec);
            if (!(action.get("type") instanceof String type) || type.isBlank()) {
                return false;
            }
            if (!"toggleEffect".equals(type)) {
                return false;
            }
            if (!isEffectCurrentlyActive(action, ctx, targetRef)) {
                return false;
            }
            foundToggle = true;
        }
        return foundToggle;
    }

    private static boolean isEffectCurrentlyActive(@Nonnull Map<String, Object> action,
                                                   @Nonnull SkillRuntimeContext ctx,
                                                   @Nonnull Ref<EntityStore> targetRef) {
        if (!(action.get("effectId") instanceof String effectId) || effectId.isBlank()) {
            return false;
        }

        EffectDef effectDef = definitionProvider.getEffect(effectId);
        if (effectDef == null) {
            return false;
        }

        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) {
            return false;
        }

        EffectControllerComponent ec = (EffectControllerComponent) ctx.store()
                .getComponent(targetRef, EffectControllerComponent.getComponentType());
        return ec != null && ec.hasEffect(effectIndex);
    }
}
