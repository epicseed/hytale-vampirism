package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.hytale.DamageAdapter;
import com.epicseed.vampirism.hytale.EffectAdapter;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.runtime.actions.AbilityActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.vampirism.skill.runtime.actions.BloodActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.ChannelActionHandlers;
import com.epicseed.vampirism.skill.runtime.actions.ModifierActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.PresentationActionHandlers;
import com.epicseed.vampirism.skill.runtime.actions.SkillActionHandler;
import com.epicseed.vampirism.skill.runtime.actions.StatActionHandler;
import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.epicseed.vampirism.util.WorldPositionHelper;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.Holder;
import com.epicseed.vampirism.skill.model.EffectDef;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SkillActionExecutor {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Fallback teleport range when no explicit maxRange is configured. */
    private static final double DEFAULT_TELEPORT_RANGE = 8.0;
    private static final double TELEPORT_LOOK_START_OFFSET = 0.5;
    private static final double TELEPORT_MIN_DISTANCE = 1.5;
    private static final double TELEPORT_WALL_BUFFER = 0.3;
    private static final Field PROJECTILE_DAMAGE_MODIFIER_FIELD = resolveProjectileDamageModifierField();
    private static final ActionHandlerRegistry ACTION_HANDLERS = createActionHandlers();

    private SkillActionExecutor() {}

    public static boolean executeAll(List<Map<String, Object>> actions, SkillRuntimeContext ctx) {
        if (actions == null || actions.isEmpty()) return false;
        boolean executedAny = false;
        for (Map<String, Object> action : actions) {
            executedAny |= execute(action, ctx);
        }
        return executedAny;
    }

    public static boolean execute(Map<String, Object> action, SkillRuntimeContext ctx) {
        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveAction(action);
        Object type = resolved.get("type");
        if (!(type instanceof String typeId) || typeId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] Action without type: " + resolved);
            return false;
        }
        if (!evaluateActionConditions(resolved, ctx)) {
            return false;
        }

        SkillActionHandler handler = ACTION_HANDLERS.find(typeId);
        if (handler == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Unsupported action type: " + typeId);
            return false;
        }
        return handler.execute(resolved, ctx);
    }

    private static ActionHandlerRegistry createActionHandlers() {
        return new ActionHandlerRegistry()
                .register("applyEffect", SkillActionExecutor::applyEffect)
                .register("removeEffect", SkillActionExecutor::removeEffect)
                .register("toggleEffect", SkillActionExecutor::toggleEffect)
                .register("healSelf", StatActionHandler::healSelf)
                .register("dealDamage", SkillActionExecutor::dealDamage)
                .register("executeFinalBlow", SkillActionExecutor::executeFinalBlow)
                .register("startFeedChannel", ChannelActionHandlers::startFeedChannel)
                .register("startHealthToBloodChannel", ChannelActionHandlers::startHealthToBloodChannel)
                .register("spawnProjectile", SkillActionExecutor::spawnProjectile)
                .register("teleport", SkillActionExecutor::teleport)
                .register("activateAbility", AbilityActionHandler::activateAbility)
                .register("grantTemporaryModifier", ModifierActionHandler::grantTemporaryModifier)
                .register("modifyStat", StatActionHandler::modifyStat)
                .register("modifyBlood", BloodActionHandler::modifyBlood)
                .register("playSound", PresentationActionHandlers::playSound)
                .register("spawnParticles", PresentationActionHandlers::spawnParticles)
                .register("sendMessage", PresentationActionHandlers::sendMessage)
                .register("applyTimedSpeedBoost", ModifierActionHandler::grantTemporaryModifierLegacySpeed)
                .register("applyControlEffect", SkillActionExecutor::deprecatedApplyControlEffect)
                .register("highlightEnemies", SkillActionExecutor::deprecatedHighlightEnemies);
    }

    private static boolean deprecatedApplyControlEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        LOGGER.atWarning().log("[SkillActionExecutor] 'applyControlEffect' is deprecated; migrate to 'applyEffect' with an effectId + targetingId: " + action);
        return false;
    }

    private static boolean deprecatedHighlightEnemies(Map<String, Object> action, SkillRuntimeContext ctx) {
        LOGGER.atWarning().log("[SkillActionExecutor] 'highlightEnemies' is deprecated; migrate to 'applyEffect' with an effectId + targetingId: " + action);
        return false;
    }

    private static boolean applyEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object effectValue = action.get("effectId");
        if (!(effectValue instanceof String effectId) || effectId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] applyEffect missing effectId: " + action);
            return false;
        }

        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectId);
        if (effectDef == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Unknown effect definition: " + effectId);
            return false;
        }

        if (!SkillRequirementEvaluator.evaluateAll(effectDef.requirements, ctx)) {
            return false;
        }

        int effectIndex = EffectAdapter.resolveEffectIndex(effectDef.effectId);
        if (effectIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect not found: " + effectDef.effectId);
            return false;
        }

        var effect = EffectAdapter.resolveEffect(effectIndex);
        if (effect == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect asset missing: " + effectDef.effectId);
            return false;
        }

        float duration = resolveEffectDuration(effectDef, ctx);
        // Optional per-action override (used by migrated 'highlightEnemies' / 'applyControlEffect' actions).
        if (action.get("durationSeconds") instanceof Number n) {
            duration = n.floatValue();
        }

        // Fan-out: honor explicit targetingId by resolving it and applying to each entity.
        List<Ref<EntityStore>> targets;
        Object targetingIdValue = action.get("targetingId");
        if (targetingIdValue instanceof String targetingId && !targetingId.isBlank()) {
            TargetingResult result = TargetingResolver.resolve(Map.of("targetingId", targetingId), ctx);
            targets = result.targets();
            if (targets.isEmpty()) return false;
        } else {
            Ref<EntityStore> targetRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();
            targets = List.of(targetRef);
        }

        String conditionId = action.get("conditionId") instanceof String s ? s : null;
        boolean anyApplied = false;
        for (Ref<EntityStore> targetRef : targets) {
            if (conditionId != null) {
                SkillRuntimeContext targetCtx = ctx.withTarget(targetRef);
                if (!SkillConditionEvaluator.evaluate(Map.of("conditionId", conditionId), targetCtx)) continue;
            }
            anyApplied |= EffectAdapter.applyOrReplace(targetRef, effectIndex, effect, duration, ctx.store());
        }
        return anyApplied;
    }

    /**
     * Removes an effect by {@code effectId} from the resolved target(s).
     *
     * <p>Action spec: {@code { "type": "removeEffect", "effectId": "...", "targetingId": "..." }}
     */
    private static boolean removeEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object effectValue = action.get("effectId");
        if (!(effectValue instanceof String effectId) || effectId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] removeEffect missing effectId: " + action);
            return false;
        }
        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectId);
        String hytaleEffectId = effectDef != null ? effectDef.effectId : effectId;
        int effectIndex = EffectAdapter.resolveEffectIndex(hytaleEffectId);
        if (effectIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] removeEffect: Hytale effect not found: " + hytaleEffectId);
            return false;
        }

        List<Ref<EntityStore>> targets;
        Object targetingIdValue = action.get("targetingId");
        if (targetingIdValue instanceof String targetingId && !targetingId.isBlank()) {
            TargetingResult result = TargetingResolver.resolve(Map.of("targetingId", targetingId), ctx);
            targets = result.targets();
            if (targets.isEmpty()) return false;
        } else {
            targets = List.of(ctx.targetRef() != null ? ctx.targetRef() : ctx.ref());
        }

        boolean anyRemoved = false;
        for (Ref<EntityStore> targetRef : targets) {
            anyRemoved |= EffectAdapter.removeIfPresent(targetRef, effectIndex, ctx.store());
        }
        return anyRemoved;
    }

    private static boolean toggleEffect(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object effectValue = action.get("effectId");
        if (!(effectValue instanceof String effectId) || effectId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] toggleEffect missing effectId: " + action);
            return false;
        }

        EffectDef effectDef = Vampirism.getInstance().GetEffectDefRegistry().Get(effectId);
        if (effectDef == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Unknown effect definition: " + effectId);
            return false;
        }

        int effectIndex = EffectAdapter.resolveEffectIndex(effectDef.effectId);
        if (effectIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect not found: " + effectDef.effectId);
            return false;
        }

        var effect = EffectAdapter.resolveEffect(effectIndex);
        if (effect == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect asset missing: " + effectDef.effectId);
            return false;
        }

        Ref<EntityStore> targetRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();

        boolean wasActive = EffectAdapter.hasEffect(targetRef, effectIndex, ctx.store());
        if (wasActive) {
            EffectAdapter.removeIfPresent(targetRef, effectIndex, ctx.store());
        } else if (!EffectAdapter.applyOrReplace(targetRef, effectIndex, effect, resolveEffectDuration(effectDef, ctx), ctx.store())) {
            LOGGER.atWarning().log("[SkillActionExecutor] Target has no EffectControllerComponent for toggleEffect: " + action);
            return false;
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Damage
    // -------------------------------------------------------------------------

    /**
     * Deals damage to the action's resolved targets.
     *
     * <p>Damage resolution (in order of precedence):
     * <ol>
     *   <li>Literal {@code amount} (float) — used directly.</li>
     *   <li>{@code statId} — resolved via {@link ModifierRegistry} and optionally scaled by
     *       {@code multiplier} (default {@code 1.0}).</li>
     *   <li>Default: {@code PROJECTILE_DAMAGE} base of 10 scaled by multiplier (legacy behavior).</li>
     * </ol>
     * When {@code selfDamage: true} the caster also takes a smaller fraction of the damage.
     */
    private static boolean dealDamage(Map<String, Object> action, SkillRuntimeContext ctx) {
        List<Ref<EntityStore>> targets = resolveActionTargets(action, ctx);
        if (targets.isEmpty()) {
            LOGGER.atWarning().log("[SkillActionExecutor] dealDamage: no targets resolved");
            return false;
        }

        float multiplier = action.get("multiplier") instanceof Number m ? m.floatValue() : 1.0f;
        float baseDamage;
        if (action.get("amount") instanceof Number a) {
            baseDamage = a.floatValue() * multiplier;
        } else if (action.get("statId") instanceof String statId && !statId.isBlank()) {
            VampireStatType stat;
            try {
                stat = VampireStatType.valueOf(statId);
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("[SkillActionExecutor] dealDamage: unknown statId: " + statId);
                return false;
            }
            baseDamage = ModifierRegistry.get().compute(stat, 0f, ctx.modifierContext()) * multiplier;
        } else {
            // Legacy default
            baseDamage = ModifierRegistry.get().compute(VampireStatType.PROJECTILE_DAMAGE, 10f, ctx.modifierContext()) * multiplier;
        }

        boolean selfDamage = Boolean.TRUE.equals(action.get("selfDamage"));
        boolean anyHit = false;

        for (Ref<EntityStore> targetRef : targets) {
            anyHit |= applyDamage(ctx.ref(), targetRef, baseDamage, ctx);
        }

        if (selfDamage) {
            float selfDamageMultiplier = Math.max(0f, ModifierRegistry.get().compute(
                    VampireStatType.SELF_DAMAGE_MULTIPLIER, 1f, ctx.modifierContext()));
            float selfDmg = baseDamage * 0.3f * selfDamageMultiplier;
            if (selfDmg > 0f) {
                applyDamage(ctx.ref(), ctx.ref(), selfDmg, ctx);
            }
        }
        return anyHit;
    }

    // -------------------------------------------------------------------------
    // Execute (Devour)
    // -------------------------------------------------------------------------

    /**
     * Instantly kills the target entity when its HP is at or below a threshold.
     *
     * <p>Threshold resolution (in order of precedence):
     * <ol>
     *   <li>Explicit {@code threshold} (0..1) on the action.</li>
     *   <li>{@code statId} — resolves the threshold via {@link ModifierRegistry}.</li>
     *   <li>Legacy {@code requirementId} — evaluated as a boolean gate.</li>
     * </ol>
     */
    private static boolean executeFinalBlow(Map<String, Object> action, SkillRuntimeContext ctx) {
        Ref<EntityStore> targetRef = ctx.targetRef();
        if (targetRef == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: no targetRef in context");
            return false;
        }

        EntityStatMap stats = (EntityStatMap) ctx.store().getComponent(targetRef, EntityStatMap.getComponentType());
        if (stats == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: target has no EntityStatMap");
            return false;
        }
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: target has no health stat");
            return false;
        }

        // Resolve threshold via the new data-driven fields; fall back to the legacy requirementId gate.
        Float threshold = null;
        if (action.get("threshold") instanceof Number n) {
            threshold = n.floatValue();
        } else if (action.get("statId") instanceof String statId && !statId.isBlank()) {
            try {
                VampireStatType stat = VampireStatType.valueOf(statId);
                threshold = ModifierRegistry.get().compute(stat, 0f, ctx.modifierContext());
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: unknown statId: " + statId);
                return false;
            }
        }
        if (threshold != null) {
            float hpPercent = health.getMax() > 0f ? health.get() / health.getMax() : 0f;
            if (hpPercent > threshold) return false;
        } else {
            String reqId = action.get("requirementId") instanceof String s ? s : null;
            if (reqId != null) {
                Map<String, Object> reqSpec = Map.of("requirementId", reqId);
                if (!SkillRequirementEvaluator.evaluate(reqSpec, ctx)) {
                    LOGGER.atWarning().log("[SkillActionExecutor] executeFinalBlow: requirement not met (" + reqId + ")");
                    return false;
                }
            }
        }

        float executeDamage = Math.max(health.get(), health.getMax()) + 9999f;
        applyDamage(ctx.ref(), targetRef, executeDamage, ctx);
        PassiveService.get().onFeed(ctx);
        LOGGER.atInfo().log("[SkillActionExecutor] executeFinalBlow: target executed");
        return true;
    }

    // -------------------------------------------------------------------------
    // Projectile (Blood Throw)
    // -------------------------------------------------------------------------

    /**
     * Launches a native Hytale projectile from the caster's current look transform.
     *
     * <p>Action spec fields:
     * <ul>
     *   <li>{@code projectileId} (required) — id of the Hytale projectile asset.</li>
     *   <li>{@code damageStatId} (optional) — overrides {@link VampireStatType#PROJECTILE_DAMAGE}
     *       as the damage stat used to scale the projectile's damage multiplier.</li>
     *   <li>{@code speedStatId} (optional) — overrides {@link VampireStatType#PROJECTILE_SPEED}
     *       as the speed stat used to scale the projectile's velocity.</li>
     * </ul>
     */
    private static boolean spawnProjectile(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object projectileIdValue = action.get("projectileId");
        if (!(projectileIdValue instanceof String projectileId) || projectileId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile missing projectileId: " + action);
            return false;
        }
        UUID ownerUuid = extractEntityUuid(ctx.ref(), ctx.store());
        if (ownerUuid == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile requires UUIDComponent-backed entity context");
            return false;
        }
        TimeResource time = ctx.store().getResource(TimeResource.getResourceType());
        if (time == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: missing TimeResource");
            return false;
        }
        Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
        if (look == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: failed to resolve look transform");
            return false;
        }

        Holder<EntityStore> holder = ProjectileComponent.assembleDefaultProjectile(
                time, projectileId, look.getPosition(), look.getRotation());
        ProjectileComponent projectile = holder.getComponent(ProjectileComponent.getComponentType());
        if (projectile == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: projectile holder missing component");
            return false;
        }

        holder.ensureComponent(Intangible.getComponentType());
        if (projectile.getProjectile() == null && !projectile.initialize()) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: failed to initialize projectile " + projectileId);
            return false;
        }
        if (projectile.getProjectile() == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: projectile asset not found " + projectileId);
            return false;
        }

        Vector3d position = look.getPosition();
        projectile.shoot(
                holder,
                ownerUuid,
                position.getX(),
                position.getY(),
                position.getZ(),
                look.getRotation().getYaw(),
                look.getRotation().getPitch());
        tuneProjectileFromStats(holder, projectile, ctx, action);
        ctx.store().addEntity(holder, AddReason.SPAWN);
        LOGGER.atInfo().log("[SkillActionExecutor] spawnProjectile: launched " + projectileId);
        return true;
    }

    @Nullable
    private static UUID extractEntityUuid(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uuidComponent = (UUIDComponent) store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComponent != null ? uuidComponent.getUuid() : null;
    }

    // -------------------------------------------------------------------------
    // Teleport
    // -------------------------------------------------------------------------

    /**
     * Teleports the caster relative to their look transform or toward a target.
     *
     * <p>Action spec fields:
     * <ul>
     *   <li>{@code distance} (blocks, optional) — overrides the targeting spec's {@code maxRange}.</li>
     *   <li>{@code mode} (optional) — one of:
     *     <ul>
     *       <li>{@code forward} (default) — along the caster's look direction.</li>
     *       <li>{@code toTarget}  — snaps to {@code ctx.targetRef()}'s position.</li>
     *       <li>{@code toLook}    — legacy alias of {@code forward}.</li>
     *     </ul>
     *   </li>
     * </ul>
     */
    private static boolean teleport(Map<String, Object> action, SkillRuntimeContext ctx) {
        TransformComponent transform = (TransformComponent) ctx.store().getComponent(
                ctx.ref(), TransformComponent.getComponentType());
        if (transform == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] teleport: caster has no TransformComponent");
            return false;
        }

        World world = resolveWorld(ctx);
        if (world == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] teleport: caster world unavailable");
            return false;
        }

        String mode = action.get("mode") instanceof String s ? s : "forward";
        Vector3d target;

        if ("toTarget".equals(mode)) {
            Ref<EntityStore> targetRef = ctx.targetRef();
            if (targetRef == null || targetRef.equals(ctx.ref())) {
                LOGGER.atWarning().log("[SkillActionExecutor] teleport toTarget: no targetRef");
                return false;
            }
            TransformComponent tt = (TransformComponent) ctx.store().getComponent(targetRef, TransformComponent.getComponentType());
            if (tt == null) return false;
            target = tt.getPosition();
        } else {
            Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
            if (look == null) {
                LOGGER.atWarning().log("[SkillActionExecutor] teleport: caster look transform unavailable");
                return false;
            }

            double teleportRange = resolveTeleportDistance(action);
            target = resolveLookTeleportTarget(world, look, teleportRange);
            if (target == null) {
                LOGGER.atWarning().log("[SkillActionExecutor] teleport: unable to resolve a safe look target");
                return false;
            }
        }

        Vector3d safeTarget = WorldPositionHelper.findSafeGroundPosition(world, target);
        if (safeTarget != null) {
            target = safeTarget;
        }

        try {
            Teleport teleport = Teleport.createForPlayer(world, target, transform.getRotation());
            HeadRotation headRotation = (HeadRotation) ctx.store().getComponent(ctx.ref(), HeadRotation.getComponentType());
            if (headRotation != null) {
                teleport.setHeadRotation(headRotation.getRotation());
            }
            ctx.store().putComponent(ctx.ref(), Teleport.getComponentType(), teleport);
            LOGGER.atInfo().log(String.format("[SkillActionExecutor] teleport: moved to %.1f,%.1f,%.1f",
                    target.x, target.y, target.z));
            return true;
        } catch (Exception ex) {
            LOGGER.atWarning().log("[SkillActionExecutor] teleport: native teleport failed — " + ex.getMessage());
            return false;
        }
    }

    private static double resolveTeleportDistance(Map<String, Object> action) {
        if (action.get("distance") instanceof Number d) {
            return d.doubleValue() > 0d ? d.doubleValue() : DEFAULT_TELEPORT_RANGE;
        }
        Object targetingIdObj = action.get("targetingId");
        if (targetingIdObj instanceof String targetingId && !targetingId.isBlank()) {
            Map<String, Object> targetingSpec = SkillRuntimeDefinitions.resolveTargeting(Map.of("targetingId", targetingId));
            if (targetingSpec.get("maxRange") instanceof Number r && r.doubleValue() > 0d) {
                return r.doubleValue();
            }
        }
        return DEFAULT_TELEPORT_RANGE;
    }

    private static World resolveWorld(@Nonnull SkillRuntimeContext ctx) {
        EntityStore entityStore = ctx.store().getExternalData();
        return entityStore != null ? entityStore.getWorld() : null;
    }

    private static Vector3d resolveLookTeleportTarget(@Nonnull World world,
                                                      @Nonnull Transform look,
                                                      double teleportRange) {
        Vector3d position = look.getPosition();
        Vector3d direction = look.getDirection();
        if (direction == null || direction.length() < 0.001d) {
            return null;
        }

        Vector3d directionNormalized = direction.clone().normalize();
        Vector3d raycastStart = directionNormalized.clone().scale(TELEPORT_LOOK_START_OFFSET).add(position);
        Vector3i hitBlock = TargetUtil.getTargetBlock(world, (blockId, fluidId) -> isSolidBlockId(blockId),
                raycastStart.x, raycastStart.y, raycastStart.z,
                directionNormalized.x, directionNormalized.y, directionNormalized.z,
                teleportRange);

        if (hitBlock == null) {
            return directionNormalized.clone().scale(teleportRange).add(position);
        }

        Vector3d blockMin = new Vector3d(hitBlock.x, hitBlock.y, hitBlock.z);
        Vector3d blockMax = new Vector3d(hitBlock.x + 1.0, hitBlock.y + 1.0, hitBlock.z + 1.0);
        double minDist = Double.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int k = 0; k < 2; k++) {
                    Vector3d corner = new Vector3d(
                            i == 0 ? blockMin.x : blockMax.x,
                            j == 0 ? blockMin.y : blockMax.y,
                            k == 0 ? blockMin.z : blockMax.z);
                    double dist = corner.clone().subtract(position).dot(directionNormalized);
                    if (dist > 0.0d && dist < minDist) {
                        minDist = dist;
                    }
                }
            }
        }

        if (minDist < Double.MAX_VALUE && minDist >= TELEPORT_MIN_DISTANCE) {
            return directionNormalized.clone().scale(minDist - TELEPORT_WALL_BUFFER).add(position);
        }
        if (minDist < Double.MAX_VALUE && minDist < TELEPORT_MIN_DISTANCE) {
            return position;
        }
        return directionNormalized.clone().scale(teleportRange).add(position);
    }

    private static boolean isSolidBlockId(int blockId) {
        if (blockId == 0) {
            return false;
        }
        BlockType blockType = (BlockType) BlockType.getAssetMap().getAsset(blockId);
        return blockType != null && blockType.getMaterial() == BlockMaterial.Solid;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the action-level targets for actions that embed their own {@code targetingId}
     * (e.g. {@code applyEffect}, {@code dealDamage}).  Falls back to {@code ctx.targetRef()}
     * when no targeting spec is embedded.
     */
    private static List<Ref<EntityStore>> resolveActionTargets(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object targetingValue = action.get("targetingId");
        if (targetingValue instanceof String targetingId && !targetingId.isBlank()) {
            var result = TargetingResolver.resolve(Map.of("targetingId", targetingId), ctx);
            if (result.hasTargets()) return result.targets();
        }
        if (ctx.targetRef() != null && !ctx.targetRef().equals(ctx.ref())) return List.of(ctx.targetRef());
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static boolean evaluateActionConditions(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object conditionsObj = action.get("conditions");
        if (!(conditionsObj instanceof List<?> rawConditions) || rawConditions.isEmpty()) {
            return true;
        }
        try {
            return SkillConditionEvaluator.evaluateAll((List<Map<String, Object>>) rawConditions, ctx);
        } catch (ClassCastException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] Malformed action conditions: " + action);
            return false;
        }
    }

    private static float resolveEffectDuration(@Nonnull EffectDef effectDef, @Nonnull SkillRuntimeContext ctx) {
        if (effectDef.duration <= 0f) {
            return effectDef.duration;
        }
        float multiplier = Math.max(0f, ModifierRegistry.get().compute(
                VampireStatType.ABILITY_DURATION_MULTIPLIER, 1f, ctx.modifierContext()));
        return effectDef.duration * multiplier;
    }

    private static boolean applyDamage(@Nonnull Ref<EntityStore> sourceRef,
                                       @Nonnull Ref<EntityStore> targetRef,
                                       float amount,
                                       @Nonnull SkillRuntimeContext ctx) {
        return DamageAdapter.executePhysicalDamage(sourceRef, targetRef, ctx.store(), amount);
    }

    private static void tuneProjectileFromStats(@Nonnull Holder<EntityStore> holder,
                                                @Nonnull ProjectileComponent projectile,
                                                @Nonnull SkillRuntimeContext ctx,
                                                @Nonnull Map<String, Object> action) {
        if (projectile.getProjectile() == null) return;

        VampireStatType damageStat = resolveStatType(action.get("damageStatId"), VampireStatType.PROJECTILE_DAMAGE);
        VampireStatType speedStat  = resolveStatType(action.get("speedStatId"),  VampireStatType.PROJECTILE_SPEED);

        float baseDamage = Math.max(0f, projectile.getProjectile().getDamage());
        if (baseDamage > 0f) {
            float tunedDamage = ModifierRegistry.get().compute(damageStat, baseDamage, ctx.modifierContext());
            setProjectileDamageMultiplier(projectile, tunedDamage / baseDamage);
        }

        float speedMultiplier = Math.max(0f, ModifierRegistry.get().compute(
                speedStat, 1f, ctx.modifierContext()));
        if (Math.abs(speedMultiplier - 1f) < 0.0001f) return;

        SimplePhysicsProvider physics = projectile.getSimplePhysicsProvider();
        if (physics == null) return;

        Vector3d velocity = new Vector3d(physics.getVelocity());
        if (velocity.length() > 0d) {
            velocity.setLength(velocity.length() * speedMultiplier);
            physics.setVelocity(velocity);
        }

        BoundingBox boundingBox = holder.getComponent(BoundingBox.getComponentType());
        if (boundingBox != null) {
            double terminalVelocity = projectile.getProjectile().getTerminalVelocity() * speedMultiplier;
            physics.setTerminalVelocities(terminalVelocity, terminalVelocity, boundingBox);
        }
    }

    private static VampireStatType resolveStatType(Object rawId, VampireStatType fallback) {
        if (rawId instanceof String s && !s.isBlank()) {
            try {
                return VampireStatType.valueOf(s);
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("[SkillActionExecutor] Unknown stat id '" + s + "', using default " + fallback);
            }
        }
        return fallback;
    }

    private static void setProjectileDamageMultiplier(@Nonnull ProjectileComponent projectile, float multiplier) {
        if (PROJECTILE_DAMAGE_MODIFIER_FIELD == null) return;
        try {
            PROJECTILE_DAMAGE_MODIFIER_FIELD.setFloat(projectile, Math.max(0f, multiplier));
        } catch (IllegalAccessException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] Failed to tune projectile damage: " + e.getMessage());
        }
    }

    private static Field resolveProjectileDamageModifierField() {
        try {
            Field field = ProjectileComponent.class.getDeclaredField("brokenDamageModifier");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] Projectile damage tuning unavailable: " + e.getMessage());
            return null;
        }
    }

}
