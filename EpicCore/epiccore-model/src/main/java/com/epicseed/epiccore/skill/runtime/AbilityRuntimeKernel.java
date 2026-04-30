package com.epicseed.epiccore.skill.runtime;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.Skill;
import com.epicseed.epiccore.skill.runtime.actions.SkillActionExecutor;

public final class AbilityRuntimeKernel<CTX extends AbilityRuntimeContext<TARGET, CTX>, TARGET> {

    private static final Logger LOGGER = Logger.getLogger(AbilityRuntimeKernel.class.getName());
    private static final int MAX_ABILITY_ACTIVATION_DEPTH = 8;

    private final AbilityDefinitionProvider definitionProvider;
    private final AbilityAccessProvider accessProvider;
    private final AbilityRequirementEvaluator<CTX> requirementEvaluator;
    private final AbilityTargetResolver<CTX, TARGET> targetResolver;
    private final SkillActionExecutor<CTX> actionExecutor;
    private final AbilityResourcePort<TARGET> resourcePort;
    private final AbilityCostResolver<CTX> costResolver;
    private final AbilityActivationNotifier<CTX> activationNotifier;

    public AbilityRuntimeKernel(AbilityDefinitionProvider definitionProvider,
                                AbilityAccessProvider accessProvider,
                                AbilityRequirementEvaluator<CTX> requirementEvaluator,
                                AbilityTargetResolver<CTX, TARGET> targetResolver,
                                SkillActionExecutor<CTX> actionExecutor,
                                AbilityResourcePort<TARGET> resourcePort,
                                AbilityCostResolver<CTX> costResolver,
                                AbilityActivationNotifier<CTX> activationNotifier) {
        this.definitionProvider = definitionProvider;
        this.accessProvider = accessProvider;
        this.requirementEvaluator = requirementEvaluator;
        this.targetResolver = targetResolver;
        this.actionExecutor = actionExecutor;
        this.resourcePort = resourcePort;
        this.costResolver = costResolver;
        this.activationNotifier = activationNotifier;
    }

    public SkillActivationResult activate(String abilityId, CTX context) {
        CTX nextCtx = tryEnterAbility(abilityId, context);
        if (nextCtx == null) {
            return SkillActivationResult.failed(SkillActivationResult.Status.DENIED,
                    "Recursive ability activation blocked: " + abilityId);
        }
        return activateResolved(abilityId, nextCtx);
    }

    public SkillActivationResult activateFromAction(String abilityId, CTX context) {
        CTX nextCtx = tryEnterAbility(abilityId, context);
        if (nextCtx == null) {
            return SkillActivationResult.failed(SkillActivationResult.Status.DENIED,
                    "Recursive ability activation blocked: " + abilityId);
        }
        return activateResolved(abilityId, nextCtx);
    }

    public CTX tryEnterAbility(String abilityId, CTX context) {
        if (context.hasAbilityInActivationPath(abilityId)) {
            String path = context.activationPathString();
            LOGGER.warning("[AbilityRuntimeKernel] Blocked recursive ability activation: "
                    + (path.isBlank() ? abilityId : path + " -> " + abilityId));
            return null;
        }
        if (context.activationDepth() >= MAX_ABILITY_ACTIVATION_DEPTH) {
            String path = context.activationPathString();
            LOGGER.warning("[AbilityRuntimeKernel] Blocked ability activation chain depth > "
                    + MAX_ABILITY_ACTIVATION_DEPTH + ": "
                    + (path.isBlank() ? abilityId : path + " -> " + abilityId));
            return null;
        }
        return context.withActivatedAbility(abilityId);
    }

    private SkillActivationResult activateResolved(String abilityId, CTX context) {
        Ability ability = definitionProvider.getAbility(abilityId);
        if (ability == null) {
            return SkillActivationResult.unknownAbility(abilityId);
        }

        UUID uuid = context.uuid();
        if (uuid == null) {
            return SkillActivationResult.failed(SkillActivationResult.Status.DENIED,
                    "Missing UUID in runtime context for: " + abilityId);
        }

        if (!isAbilityUnlocked(uuid, abilityId)) {
            return SkillActivationResult.requirementNotMet("Ability not unlocked: " + abilityId);
        }

        if (!requirementEvaluator.evaluateAll(ability.requirements, context)) {
            return SkillActivationResult.requirementNotMet("Requirements not met for: " + abilityId);
        }

        AbilityActivationCharge charge = costResolver.resolveCharge(ability, context);
        long cooldownMs = Math.max(0L, charge.cooldownMs());
        int resourceCost = Math.max(0, charge.resourceCost());

        boolean cooldownReserved = false;
        if (cooldownMs > 0L) {
            cooldownReserved = AbilityCooldownTracker.tryUse(uuid, abilityId, cooldownMs);
            if (!cooldownReserved) {
                return SkillActivationResult.onCooldown(AbilityCooldownTracker.getRemainingMs(uuid, abilityId));
            }
        }

        try {
            if (resourceCost > 0 && !resourcePort.canAfford(context.ref(), resourceCost)) {
                return SkillActivationResult.requirementNotMet(
                        String.format("Insufficient resource for %s (need %d)", abilityId, resourceCost));
            }

            ResolvedTargets<TARGET> targeting = targetResolver.resolve(ability.targeting, context);
            String targetingType = resolveTargetingType(ability.targeting);
            if (!targeting.hasTargets()) {
                if ("target".equals(targetingType)) {
                    return SkillActivationResult.noTarget();
                }
                if ("area".equals(targetingType)) {
                    return SkillActivationResult.noTargets();
                }
            }

            boolean executedAny = false;
            if ("target".equals(targetingType) && targeting.hasTargets()) {
                for (TARGET target : targeting.targets()) {
                    executedAny |= actionExecutor.executeAll(ability.actions, context.withTarget(target));
                }
            } else {
                executedAny = actionExecutor.executeAll(ability.actions, alignExecutionContext(context, targetingType, targeting));
            }

            if (!executedAny) {
                LOGGER.warning(String.format("[AbilityRuntimeKernel] %s activation of '%s' had no successful action execution",
                        uuid, abilityId));
                return SkillActivationResult.failed(SkillActivationResult.Status.DENIED,
                        "The ability failed to apply its effects.");
            }

            if (resourceCost > 0) {
                resourcePort.spend(context.ref(), resourceCost);
            }
            cooldownReserved = false;
            activationNotifier.onActivated(context, abilityId);
            LOGGER.info(String.format("[AbilityRuntimeKernel] %s activated '%s' (cooldown=%dms, resourceCost=%d)",
                    uuid, abilityId, cooldownMs, resourceCost));
            return SkillActivationResult.success();
        } finally {
            if (cooldownReserved) {
                AbilityCooldownTracker.reset(uuid, abilityId);
            }
        }
    }

    private boolean isAbilityUnlocked(UUID uuid, String abilityId) {
        if (accessProvider.allowsTemporaryAbility(uuid, abilityId)) {
            return true;
        }
        Set<String> unlockedSkillIds = accessProvider.getUnlockedSkillIds(uuid);
        for (String skillId : unlockedSkillIds) {
            Skill skill = definitionProvider.getSkill(skillId);
            if (skill != null && abilityId.equals(skill.abilityId)) {
                return true;
            }
        }
        return false;
    }

    private String resolveTargetingType(Map<String, Object> targeting) {
        if (targeting == null || targeting.isEmpty()) {
            return "";
        }
        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveTargeting(targeting);
        return resolved.get("type") instanceof String type ? type : "";
    }

    private CTX alignExecutionContext(CTX context,
                                      String targetingType,
                                      ResolvedTargets<TARGET> targeting) {
        if ("area".equals(targetingType) || "areaAtLook".equals(targetingType)) {
            return context.withTarget(null);
        }
        TARGET first = targeting.first();
        return context.withTarget(first);
    }
}
