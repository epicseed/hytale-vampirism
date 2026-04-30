package com.epicseed.vampirism.skill.runtime;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.epiccore.skill.runtime.AbilityAccessProvider;
import com.epicseed.epiccore.skill.runtime.AbilityDefinitionProvider;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.Skill;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Central service for activating data-driven vampire abilities.
 *
 * <p>Activation pipeline:
 * <ol>
 *   <li>Look up the ability in {@link com.epicseed.vampirism.skill.registry.AbilityRegistry}.</li>
 *   <li>Verify the player has the ability unlocked via the skill tree.</li>
 *   <li>Evaluate the ability's {@code requirements} list.</li>
 *   <li>Check the per-ability cooldown (reduced by {@link VampireStatType#ABILITY_COOLDOWN_REDUCTION}).</li>
 *   <li>Verify the player can afford the blood cost (scaled by {@link VampireStatType#ABILITY_BLOOD_COST_MULTIPLIER}).</li>
 *   <li>Resolve targeting via {@link TargetingResolver}.</li>
 *   <li>Execute all actions via {@link SkillActionExecutor} for each resolved target.</li>
 *   <li>Commit: deduct blood, start cooldown.</li>
 * </ol>
 *
 * <p>Blood cost is expressed in the ability definition as integer blood units. The default
 * bar holds 100 blood, and max-capacity modifiers can raise that ceiling.
 *
 * <p>This service is stateless and all methods are thread-safe (all state lives in the
 * sub-systems it delegates to).
 */
public final class AbilityService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_ABILITY_ACTIVATION_DEPTH = 8;
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

    private AbilityService() {}

    public static void init(AbilityDefinitionProvider definitionProvider,
                            AbilityAccessProvider accessProvider,
                            AbilityResourcePort resourcePort) {
        AbilityService.definitionProvider = definitionProvider != null
                ? definitionProvider
                : UNINITIALIZED_DEFINITION_PROVIDER;
        AbilityService.accessProvider = accessProvider != null
                ? accessProvider
                : UNINITIALIZED_ACCESS_PROVIDER;
        AbilityService.resourcePort = resourcePort != null
                ? resourcePort
                : UNINITIALIZED_RESOURCE_PORT;
    }

    /**
     * Attempts to activate an ability for a player.
     *
     * @param abilityId  the ability identifier (matches {@link Ability#id})
     * @param uuid       the player's UUID
     * @param casterRef  the player's entity ref
     * @param targetRef  optional pre-resolved target; may be {@code null} for self/area abilities
     * @param store      the world entity store
     * @return a {@link SkillActivationResult} describing the outcome
     */
    @Nonnull
    public static SkillActivationResult activate(
            @Nonnull String abilityId,
            @Nonnull UUID uuid,
            @Nonnull Ref<EntityStore> casterRef,
            @Nullable Ref<EntityStore> targetRef,
            @Nonnull Store<EntityStore> store) {
        SkillRuntimeContext nextCtx = tryEnterAbility(abilityId, new SkillRuntimeContext(uuid, casterRef, targetRef, store));
        if (nextCtx == null) {
            return SkillActivationResult.failed(SkillActivationResult.Status.DENIED,
                    "Recursive ability activation blocked: " + abilityId);
        }
        return activate(abilityId, nextCtx);
    }

    @Nonnull
    public static SkillActivationResult activateFromAction(@Nonnull String abilityId,
                                                           @Nonnull SkillRuntimeContext ctx) {
        SkillRuntimeContext nextCtx = tryEnterAbility(abilityId, ctx);
        if (nextCtx == null) {
            return SkillActivationResult.failed(SkillActivationResult.Status.DENIED,
                    "Recursive ability activation blocked: " + abilityId);
        }
        return activate(abilityId, nextCtx);
    }

    @Nonnull
    static SkillActivationResult activate(@Nonnull String abilityId, @Nonnull SkillRuntimeContext ctx) {

        // 1. Look up ability definition
        Ability ability = definitionProvider.getAbility(abilityId);
        if (ability == null) {
            return SkillActivationResult.unknownAbility(abilityId);
        }

        // 2. Resolve player context
        UUID uuid = ctx.uuid();
        if (uuid == null) {
            return SkillActivationResult.failed(SkillActivationResult.Status.DENIED,
                    "Missing UUID in runtime context for: " + abilityId);
        }
        Ref<EntityStore> casterRef = ctx.ref();
        boolean toggleOffActivation = isToggleOffActivation(ability, ctx);

        // 3. Ability must be unlocked via the skill tree
        if (!isAbilityUnlocked(uuid, abilityId)) {
            return SkillActivationResult.requirementNotMet("Ability not unlocked: " + abilityId);
        }

        // 4. Evaluate ability-level requirements
        if (!SkillRequirementEvaluator.evaluateAll(ability.requirements, ctx)) {
            return SkillActivationResult.requirementNotMet("Requirements not met for: " + abilityId);
        }

        // 5. Cooldown check + reservation (with modifier-driven reduction)
        long cooldownMs = computeCooldownMs(ability, ctx);
        boolean cooldownReserved = false;
        if (!toggleOffActivation && cooldownMs > 0L) {
            cooldownReserved = AbilityCooldownTracker.tryUse(uuid, abilityId, cooldownMs);
            if (!cooldownReserved) {
                return SkillActivationResult.onCooldown(AbilityCooldownTracker.getRemainingMs(uuid, abilityId));
            }
        }

        try {
            // 6. Blood cost check
            int bloodCost = toggleOffActivation ? 0 : computeBloodCost(ability, ctx);
            if (bloodCost > 0 && !resourcePort.canAffordBlood(casterRef, bloodCost)) {
                return SkillActivationResult.requirementNotMet(
                        String.format("Insufficient blood for %s (need %d)", abilityId, bloodCost));
            }

            // 7. Resolve targeting
            TargetingResult targeting = TargetingResolver.resolve(ability.targeting, ctx);
            String targetingType = resolveTargetingType(ability.targeting);
            if (!targeting.hasTargets()) {
                if ("target".equals(targetingType)) {
                    return SkillActivationResult.noTarget();
                }
                if ("area".equals(targetingType)) {
                    return SkillActivationResult.noTargets();
                }
            }

            // 8. Execute actions. Only explicit single-target abilities fan out per resolved target.
            boolean executedAny = false;
            if ("target".equals(targetingType) && targeting.hasTargets()) {
                for (Ref<EntityStore> t : targeting.targets()) {
                    executedAny |= SkillActionExecutor.executeAll(ability.actions, ctx.withTarget(t));
                }
            } else {
                executedAny = SkillActionExecutor.executeAll(
                        ability.actions,
                        alignExecutionContext(ctx, targetingType, targeting));
            }
            if (!executedAny) {
                LOGGER.atWarning().log(String.format("[AbilityService] %s activation of '%s' had no successful action execution",
                        uuid, abilityId));
                return SkillActivationResult.failed(SkillActivationResult.Status.DENIED,
                        "The ability failed to apply its effects.");
            }

            // 9. Commit: deduct blood and keep the reserved cooldown
            if (bloodCost > 0) {
                resourcePort.spendBlood(casterRef, bloodCost);
            }
            cooldownReserved = false;
            TriggerDispatcher.dispatch(TriggerEvent.onActivate(ctx, abilityId));

            LOGGER.atInfo().log(String.format("[AbilityService] %s activated '%s' (cooldown=%dms, bloodCost=%d)",
                    uuid, abilityId, cooldownMs, bloodCost));
            return SkillActivationResult.success();
        } finally {
            if (cooldownReserved) {
                AbilityCooldownTracker.reset(uuid, abilityId);
            }
        }
    }

    @Nullable
    static SkillRuntimeContext tryEnterAbility(@Nonnull String abilityId, @Nonnull SkillRuntimeContext ctx) {
        if (ctx.hasAbilityInActivationPath(abilityId)) {
            String path = ctx.activationPathString();
            LOGGER.atWarning().log("[AbilityService] Blocked recursive ability activation: "
                    + (path.isBlank() ? abilityId : path + " -> " + abilityId));
            return null;
        }
        if (ctx.activationDepth() >= MAX_ABILITY_ACTIVATION_DEPTH) {
            String path = ctx.activationPathString();
            LOGGER.atWarning().log("[AbilityService] Blocked ability activation chain depth > "
                    + MAX_ABILITY_ACTIVATION_DEPTH + ": "
                    + (path.isBlank() ? abilityId : path + " -> " + abilityId));
            return null;
        }
        return ctx.withActivatedAbility(abilityId);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns {@code true} if any of the player's unlocked skill nodes references this abilityId. */
    private static boolean isAbilityUnlocked(@Nonnull UUID uuid, @Nonnull String abilityId) {
        if (accessProvider.allowsTemporaryAbility(uuid, abilityId)) {
            return true;
        }
        for (String skillId : accessProvider.getUnlockedSkillIds(uuid)) {
            Skill skill = definitionProvider.getSkill(skillId);
            if (skill != null && abilityId.equals(skill.abilityId)) return true;
        }
        return false;
    }

    /**
     * Computes the effective cooldown in milliseconds, applying the player's
     * {@link VampireStatType#ABILITY_COOLDOWN_REDUCTION} modifier.
     */
    private static long computeCooldownMs(@Nonnull Ability ability,
                                          @Nonnull SkillRuntimeContext ctx) {
        float overrideSeconds = ModifierRegistry.get().compute(
                VampireStatType.ABILITY_COOLDOWN_OVERRIDE_SECONDS, 0f, ctx.modifierContext());
        if (overrideSeconds > 0f) {
            return Math.max(0L, (long) (overrideSeconds * 1000L));
        }
        if (ability.cooldown <= 0f) return 0L;
        float reduction = ModifierRegistry.get().compute(
                VampireStatType.ABILITY_COOLDOWN_REDUCTION, 0f, ctx.modifierContext());
        float effectiveCooldown = ability.cooldown * Math.max(0.1f, 1f - reduction);
        return Math.max(0L, (long)(effectiveCooldown * 1000L));
    }

    /**
     * Computes the effective blood cost in blood units, applying the player's
     * {@link VampireStatType#ABILITY_BLOOD_COST_MULTIPLIER} modifier.
     */
    private static int computeBloodCost(@Nonnull Ability ability,
                                        @Nonnull SkillRuntimeContext ctx) {
        if (ability.bloodCost <= 0) return 0;
        float multiplier = ModifierRegistry.get().compute(
                VampireStatType.ABILITY_BLOOD_COST_MULTIPLIER, 1f, ctx.modifierContext());
        return Math.max(0, Math.round(ability.bloodCost * Math.max(0f, multiplier)));
    }

    @Nonnull
    private static String resolveTargetingType(@Nullable Map<String, Object> targeting) {
        if (targeting == null || targeting.isEmpty()) return "";
        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveTargeting(targeting);
        return resolved.get("type") instanceof String t ? t : "";
    }

    @Nonnull
    private static SkillRuntimeContext alignExecutionContext(@Nonnull SkillRuntimeContext ctx,
                                                             @Nonnull String targetingType,
                                                             @Nonnull TargetingResult targeting) {
        if ("area".equals(targetingType) || "areaAtLook".equals(targetingType)) {
            return ctx.withTarget(null);
        }
        if (targeting.hasTargets()) {
            return ctx.withTarget(targeting.targets().get(0));
        }
        return ctx.withTarget(null);
    }

    private static boolean isToggleOffActivation(@Nonnull Ability ability, @Nonnull SkillRuntimeContext ctx) {
        if (ability.actions == null || ability.actions.isEmpty()) {
            return false;
        }

        Ref<EntityStore> targetRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();
        if (targetRef == null) {
            return false;
        }

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

    private static boolean isEffectCurrentlyActive(
            @Nonnull Map<String, Object> action,
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
