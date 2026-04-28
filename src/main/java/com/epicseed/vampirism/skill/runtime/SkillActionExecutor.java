package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.domain.blood.BloodConversionService;
import com.epicseed.vampirism.domain.blood.FeedService;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.runtime.actions.ActionHandlerRegistry;
import com.epicseed.vampirism.skill.runtime.actions.SkillActionHandler;
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
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.ProjectileComponent;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.BoundingBox;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.modules.physics.SimplePhysicsProvider;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;
import com.hypixel.hytale.protocol.SoundCategory;

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
                .register("healSelf", SkillActionExecutor::healSelf)
                .register("dealDamage", SkillActionExecutor::dealDamage)
                .register("executeFinalBlow", SkillActionExecutor::executeFinalBlow)
                .register("startFeedChannel", SkillActionExecutor::startFeedChannel)
                .register("startHealthToBloodChannel", SkillActionExecutor::startHealthToBloodChannel)
                .register("spawnProjectile", SkillActionExecutor::spawnProjectile)
                .register("teleport", SkillActionExecutor::teleport)
                .register("activateAbility", SkillActionExecutor::activateAbility)
                .register("grantTemporaryModifier", SkillActionExecutor::grantTemporaryModifier)
                .register("modifyStat", SkillActionExecutor::modifyStat)
                .register("modifyBlood", SkillActionExecutor::modifyBlood)
                .register("playSound", SkillActionExecutor::playSound)
                .register("spawnParticles", SkillActionExecutor::spawnParticles)
                .register("sendMessage", SkillActionExecutor::sendMessage)
                .register("applyTimedSpeedBoost", SkillActionExecutor::grantTemporaryModifierLegacySpeed)
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

        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect not found: " + effectDef.effectId);
            return false;
        }

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
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
            EffectControllerComponent ec = (EffectControllerComponent) ctx.store().getComponent(
                    targetRef, EffectControllerComponent.getComponentType());
            if (ec == null) continue;
            if (duration > 0f) {
                ec.addEffect(targetRef, effectIndex, effect, duration, OverlapBehavior.OVERWRITE, ctx.store());
            } else {
                ec.addInfiniteEffect(targetRef, effectIndex, effect, ctx.store());
            }
            anyApplied = true;
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
        int effectIndex = EntityEffect.getAssetMap().getIndex(hytaleEffectId);
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
            EffectControllerComponent ec = (EffectControllerComponent) ctx.store().getComponent(
                    targetRef, EffectControllerComponent.getComponentType());
            if (ec == null || !ec.hasEffect(effectIndex)) continue;
            ec.removeEffect(targetRef, effectIndex, ctx.store());
            anyRemoved = true;
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

        int effectIndex = EntityEffect.getAssetMap().getIndex(effectDef.effectId);
        if (effectIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect not found: " + effectDef.effectId);
            return false;
        }

        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectIndex);
        if (effect == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Hytale effect asset missing: " + effectDef.effectId);
            return false;
        }

        Ref<EntityStore> targetRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();

        EffectControllerComponent ec = (EffectControllerComponent) ctx.store().getComponent(
                targetRef, EffectControllerComponent.getComponentType());
        if (ec == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] Target has no EffectControllerComponent for toggleEffect: " + action);
            return false;
        }

        if (ec.hasEffect(effectIndex)) {
            ec.removeEffect(targetRef, effectIndex, ctx.store());
        } else {
            float duration = resolveEffectDuration(effectDef, ctx);
            if (duration > 0f) {
                ec.addEffect(targetRef, effectIndex, effect, duration, OverlapBehavior.OVERWRITE, ctx.store());
            } else {
                ec.addInfiniteEffect(targetRef, effectIndex, effect, ctx.store());
            }
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

    private static boolean startFeedChannel(Map<String, Object> action, SkillRuntimeContext ctx) {
        return FeedService.startChannel(ctx, action);
    }

    private static boolean startHealthToBloodChannel(Map<String, Object> action, SkillRuntimeContext ctx) {
        return BloodConversionService.startChannel(ctx, action);
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
    // Activate another ability (auto_enter_bat_form / Emergency Retreat)
    // -------------------------------------------------------------------------

    /**
     * Activates another named ability through {@link AbilityService}, respecting cooldown and
     * blood cost, for the same caster.
     *
     * <p>Action spec: {@code { "type": "activateAbility", "abilityId": "BatForm" }}
     */
    private static boolean activateAbility(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object abilityIdObj = action.get("abilityId");
        if (!(abilityIdObj instanceof String abilityId) || abilityId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] activateAbility missing abilityId: " + action);
            return false;
        }
        if (ctx.uuid() == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] activateAbility: no UUID in context");
            return false;
        }

        SkillRuntimeContext nextCtx = AbilityService.tryEnterAbility(abilityId, ctx);
        if (nextCtx == null) {
            return false;
        }

        var result = AbilityService.activate(abilityId, nextCtx);
        if (result.isFailure()) {
            LOGGER.atInfo().log("[SkillActionExecutor] activateAbility '" + abilityId + "': " + result.reason());
        }
        return result.isSuccess();
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

    // -------------------------------------------------------------------------
    // healSelf — restores HP equal to a VampireStatType modifier value
    // -------------------------------------------------------------------------

    /**
     * Heals the caster by the value of the named {@link VampireStatType} modifier.
     *
     * <p>Action spec: {@code { "type": "healSelf", "statId": "BITE_HEAL_AMOUNT" }}
     */
    private static boolean healSelf(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object statIdObj = action.get("statId");
        if (!(statIdObj instanceof String statId) || statId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] healSelf missing statId: " + action);
            return false;
        }

        VampireStatType statType;
        try {
            statType = VampireStatType.valueOf(statId);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] healSelf unknown statId: " + statId);
            return false;
        }

        float healAmount = ModifierRegistry.get().compute(statType, 0f, ctx.modifierContext());
        float healingReceived = Math.max(0f, ModifierRegistry.get().compute(
                VampireStatType.HEALING_RECEIVED, 1f, ctx.modifierContext()));
        healAmount *= healingReceived;
        if (healAmount <= 0f) return false;

        EntityStatMap stats = (EntityStatMap) ctx.store().getComponent(
                ctx.ref(), EntityStatMap.getComponentType());
        if (stats == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] healSelf: no EntityStatMap for " + ctx.uuid());
            return false;
        }

        stats.addStatValue(DefaultEntityStatTypes.getHealth(), healAmount);
        LOGGER.atFine().log("[SkillActionExecutor] healSelf: +" + healAmount + " HP for " + ctx.uuid());
        return true;
    }

    // -------------------------------------------------------------------------
    // grantTemporaryModifier — generic temporary stat boost (replaces applyTimedSpeedBoost)
    // -------------------------------------------------------------------------

    /**
     * Registers a temporary stat boost with {@link TemporaryModifierTracker}.
     *
     * <p>Action spec fields:
     * <ul>
     *   <li>{@code statId} (required) — {@link VampireStatType} name to boost.</li>
     *   <li>{@code amount} (optional) — additive delta. Mutually exclusive with {@code multiplier}.</li>
     *   <li>{@code multiplier} (optional) — multiplicative factor applied on top of the stat.</li>
     *   <li>{@code duration} (seconds, required) — lifetime of the boost.</li>
     *   <li>{@code stacking} (optional) — {@code replace|refresh|stack}; default {@code replace}.</li>
     *   <li>{@code amountStatId} / {@code durationStatId} (optional) — read amount/duration from
     *       a modifier stat instead of a literal.</li>
     * </ul>
     */
    private static boolean grantTemporaryModifier(Map<String, Object> action, SkillRuntimeContext ctx) {
        if (ctx.uuid() == null) return false;

        String statId = action.get("statId") instanceof String s ? s : null;
        if (statId == null || statId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] grantTemporaryModifier missing statId: " + action);
            return false;
        }
        VampireStatType stat;
        try {
            stat = VampireStatType.valueOf(statId);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("[SkillActionExecutor] grantTemporaryModifier unknown statId: " + statId);
            return false;
        }

        boolean isMultiplicative = action.containsKey("multiplier");
        TemporaryModifierTracker.Op op = isMultiplicative
                ? TemporaryModifierTracker.Op.MULTIPLICATIVE
                : TemporaryModifierTracker.Op.ADDITIVE;

        float amount = isMultiplicative
                ? resolveStatOrLiteral(action, "amountStatId", "multiplier", 1f, ctx)
                : resolveStatOrLiteral(action, "amountStatId", "amount", 0f, ctx);
        float duration = resolveStatOrLiteral(action, "durationStatId", "duration", 10f, ctx);

        if (!isMultiplicative && amount == 0f) {
            LOGGER.atWarning().log("[SkillActionExecutor] grantTemporaryModifier: resolved amount is 0 for " + statId);
            return false;
        }
        if (duration <= 0f) return false;

        TemporaryModifierTracker.Stacking stacking = resolveStacking(action);
        TemporaryModifierTracker.addBoost(ctx.uuid(), stat, amount, duration, stacking, op);
        LOGGER.atFine().log("[SkillActionExecutor] grantTemporaryModifier: " + statId + " "
                + (isMultiplicative ? "x" : "+") + amount + " for " + duration + "s → " + ctx.uuid());
        return true;
    }

    /** Backwards-compatible bridge for the deprecated {@code applyTimedSpeedBoost} action. */
    private static boolean grantTemporaryModifierLegacySpeed(Map<String, Object> action, SkillRuntimeContext ctx) {
        Map<String, Object> rewritten = new java.util.LinkedHashMap<>(action);
        rewritten.putIfAbsent("statId", "SPEED");
        // Old action used "speedBoostStatId" → new generic "amountStatId".
        Object boostStat = rewritten.remove("speedBoostStatId");
        if (boostStat != null) rewritten.putIfAbsent("amountStatId", boostStat);
        return grantTemporaryModifier(rewritten, ctx);
    }

    private static TemporaryModifierTracker.Stacking resolveStacking(Map<String, Object> action) {
        Object raw = action.get("stacking");
        if (raw instanceof String s) {
            try {
                return TemporaryModifierTracker.Stacking.valueOf(s.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                LOGGER.atWarning().log("[SkillActionExecutor] Unknown stacking policy: " + s);
            }
        }
        return TemporaryModifierTracker.Stacking.REPLACE;
    }

    // -------------------------------------------------------------------------
    // modifyStat / modifyBlood — instant adjustments
    // -------------------------------------------------------------------------

    /**
     * Instantly adjusts an entity stat on the caster.  Currently supports
     * {@link DefaultEntityStatTypes#getHealth()} as the only persistent ECS stat; other
     * {@link VampireStatType} values are computed via modifiers and cannot be "set" here.
     *
     * <p>Action spec: {@code { "type":"modifyStat", "statId":"HEALTH", "op":"add", "amount":5 }}
     */
    private static boolean modifyStat(Map<String, Object> action, SkillRuntimeContext ctx) {
        String statId = action.get("statId") instanceof String s ? s : null;
        if (statId == null || statId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] modifyStat missing statId: " + action);
            return false;
        }
        String op = action.get("op") instanceof String s ? s : "add";
        float amount = action.get("amount") instanceof Number n ? n.floatValue() : 0f;

        if ("HEALTH".equalsIgnoreCase(statId)) {
            EntityStatMap stats = (EntityStatMap) ctx.store().getComponent(
                    ctx.ref(), EntityStatMap.getComponentType());
            if (stats == null) return false;
            EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
            if (health == null) return false;
            switch (op) {
                case "add" -> stats.addStatValue(DefaultEntityStatTypes.getHealth(), amount);
                case "set" -> stats.addStatValue(DefaultEntityStatTypes.getHealth(), amount - health.get());
                case "mul" -> stats.addStatValue(DefaultEntityStatTypes.getHealth(), health.get() * (amount - 1f));
                default -> {
                    LOGGER.atWarning().log("[SkillActionExecutor] modifyStat unsupported op: " + op);
                    return false;
                }
            }
            return true;
        }

        LOGGER.atWarning().log("[SkillActionExecutor] modifyStat: statId '" + statId + "' is a derived VampireStatType; use grantTemporaryModifier instead.");
        return false;
    }

    /**
     * Adjusts the caster's blood bar.
     *
     * <p>Action spec fields:
     * <ul>
     *   <li>{@code amount} (integer blood units; legacy fractional values are scaled from 0..1 to 0..100), or</li>
     *   <li>{@code percent} (legacy percent/fraction of max blood).</li>
     *   <li>{@code op} — one of {@code add} (default) or {@code set}.</li>
     * </ul>
     */
    private static boolean modifyBlood(Map<String, Object> action, SkillRuntimeContext ctx) {
        int amount;
        if (action.get("amount") instanceof Number n) {
            amount = normalizeBloodAmount(n.floatValue());
        } else if (action.get("percent") instanceof Number n) {
            amount = normalizeBloodPercent(n.floatValue(), VampireVitalitySystem.getMaxBlood(ctx.ref()));
        } else {
            LOGGER.atWarning().log("[SkillActionExecutor] modifyBlood missing amount/percent: " + action);
            return false;
        }
        String op = action.get("op") instanceof String s ? s : "add";
        switch (op) {
            case "add" -> {
                if (amount >= 0) {
                    com.epicseed.vampirism.systems.VampireVitalitySystem.addBlood(ctx.ref(), amount);
                } else {
                    com.epicseed.vampirism.systems.VampireVitalitySystem.spendBlood(ctx.ref(), -amount);
                }
            }
            case "set" -> {
                int current = com.epicseed.vampirism.systems.VampireVitalitySystem.getBlood(ctx.ref());
                int delta = amount - current;
                if (delta >= 0) {
                    com.epicseed.vampirism.systems.VampireVitalitySystem.addBlood(ctx.ref(), delta);
                } else {
                    com.epicseed.vampirism.systems.VampireVitalitySystem.spendBlood(ctx.ref(), -delta);
                }
            }
            default -> {
                LOGGER.atWarning().log("[SkillActionExecutor] modifyBlood unsupported op: " + op);
                return false;
            }
        }
        return true;
    }

    private static int normalizeBloodAmount(float value) {
        float normalized = value;
        if (Math.abs(normalized) <= 1f) {
            normalized *= VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS;
        }
        return Math.round(normalized);
    }

    private static int normalizeBloodPercent(float value, int maxBlood) {
        float normalized = Math.abs(value) <= 1f ? value : value / 100f;
        return Math.round(normalized * Math.max(1, maxBlood));
    }

    // -------------------------------------------------------------------------
    // playSound / spawnParticles / sendMessage — presentation primitives
    // -------------------------------------------------------------------------

    private static boolean playSound(Map<String, Object> action, SkillRuntimeContext ctx) {
        String soundId = action.get("soundId") instanceof String s ? s : null;
        if (soundId == null || soundId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] playSound missing soundId: " + action);
            return false;
        }

        int soundIndex = SoundEvent.getAssetMap().getIndex(soundId);
        if (soundIndex < 0) {
            LOGGER.atWarning().log("[SkillActionExecutor] playSound: sound asset not found " + soundId);
            return false;
        }

        String scope = action.get("scope") instanceof String s ? s : "world";
        SoundCategory category = resolveSoundCategory(action, soundIndex);
        float volume = action.get("volume") instanceof Number n ? n.floatValue() : 1.0f;
        float pitch = action.get("pitch") instanceof Number n ? n.floatValue() : 1.0f;

        if ("local".equalsIgnoreCase(scope)) {
            PlayerRef playerRef = (PlayerRef) ctx.store().getComponent(ctx.ref(), PlayerRef.getComponentType());
            if (playerRef == null) {
                LOGGER.atWarning().log("[SkillActionExecutor] playSound: local scope requires PlayerRef");
                return false;
            }
            SoundUtil.playSoundEvent2dToPlayer(playerRef, soundIndex, category, volume, pitch);
            return true;
        }

        Vector3d position = resolvePresentationPosition(action, ctx);
        if (position == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] playSound: failed to resolve playback position for " + soundId);
            return false;
        }
        SoundUtil.playSoundEvent3d(soundIndex, category, position.getX(), position.getY(), position.getZ(), volume, pitch, ctx.store());
        return true;
    }

    private static boolean spawnParticles(Map<String, Object> action, SkillRuntimeContext ctx) {
        String particleId = action.get("particleId") instanceof String s ? s : null;
        if (particleId == null || particleId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnParticles missing particleId: " + action);
            return false;
        }

        if (ParticleSystem.getAssetMap().getAsset(particleId) == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnParticles: particle asset not found " + particleId);
            return false;
        }

        Vector3d position = resolvePresentationPosition(action, ctx);
        if (position == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnParticles: failed to resolve spawn position for " + particleId);
            return false;
        }
        ParticleUtil.spawnParticleEffect(particleId, position, ctx.store());
        return true;
    }

    /**
     * Sends a chat message to the caster.
     *
     * <p>Action spec: {@code { "type":"sendMessage", "text":"...", "color":"red" }}
     */
    private static boolean sendMessage(Map<String, Object> action, SkillRuntimeContext ctx) {
        String text = action.get("text") instanceof String s ? s : null;
        if (text == null || text.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] sendMessage missing text: " + action);
            return false;
        }
        com.hypixel.hytale.server.core.entity.entities.Player player =
                (com.hypixel.hytale.server.core.entity.entities.Player) ctx.store().getComponent(
                        ctx.ref(), com.hypixel.hytale.server.core.entity.entities.Player.getComponentType());
        if (player == null) return false;
        com.hypixel.hytale.server.core.Message msg = com.hypixel.hytale.server.core.Message.raw(text);
        if (action.get("color") instanceof String color && !color.isBlank()) {
            msg.color(color);
        }
        player.sendMessage(msg);
        return true;
    }

    /**
     * Helper: reads a value either from a named {@link VampireStatType} modifier or from a literal
     * numeric key in the action map.
     */
    private static float resolveStatOrLiteral(Map<String, Object> action,
                                              String statIdKey, String literalKey,
                                              float fallback,
                                              SkillRuntimeContext ctx) {
        Object statIdObj = action.get(statIdKey);
        if (statIdObj instanceof String statId && !statId.isBlank()) {
            try {
                VampireStatType st = VampireStatType.valueOf(statId);
                return ModifierRegistry.get().compute(st, fallback, ctx.modifierContext());
            } catch (IllegalArgumentException e) {
                LOGGER.atWarning().log("[SkillActionExecutor] Unknown stat for key '" + statIdKey + "': " + statId);
            }
        }
        Object lit = action.get(literalKey);
        return lit instanceof Number n ? n.floatValue() : fallback;
    }

    private static boolean applyDamage(@Nonnull Ref<EntityStore> sourceRef,
                                       @Nonnull Ref<EntityStore> targetRef,
                                       float amount,
                                       @Nonnull SkillRuntimeContext ctx) {
        if (amount <= 0f) return false;
        Damage damage = new Damage(new Damage.EntitySource(sourceRef), DamageCause.PHYSICAL, amount);
        DamageSystems.executeDamage(targetRef, ctx.store(), damage);
        return true;
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

    private static SoundCategory resolveSoundCategory(Map<String, Object> action, int soundIndex) {
        String categoryValue = action.get("category") instanceof String s ? s : null;
        if (categoryValue == null || categoryValue.isBlank()) {
            SoundEvent soundEvent = SoundEvent.getAssetMap().getAsset(soundIndex);
            categoryValue = soundEvent != null ? soundEvent.getAudioCategoryId() : null;
        }
        if (categoryValue != null) {
            for (SoundCategory category : SoundCategory.values()) {
                if (category.name().equalsIgnoreCase(categoryValue)) {
                    return category;
                }
            }
        }
        return SoundCategory.SFX;
    }

    private static Vector3d resolvePresentationPosition(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object targetingIdObj = action.get("targetingId");
        if (targetingIdObj instanceof String targetingId && !targetingId.isBlank()) {
            Map<String, Object> targetingSpec = SkillRuntimeDefinitions.resolveTargeting(Map.of("targetingId", targetingId));
            Vector3d targetPosition = resolveTargetingPosition(targetingSpec, ctx);
            if (targetPosition != null) {
                return targetPosition;
            }
        }

        Ref<EntityStore> entityRef = ctx.targetRef() != null ? ctx.targetRef() : ctx.ref();
        Vector3d entityPosition = resolveEntityPosition(entityRef, ctx);
        if (entityPosition != null) {
            return entityPosition;
        }

        Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
        return look != null ? new Vector3d(look.getPosition()) : null;
    }

    private static Vector3d resolveTargetingPosition(Map<String, Object> targetingSpec, SkillRuntimeContext ctx) {
        Object typeObj = targetingSpec.get("type");
        if (!(typeObj instanceof String typeId) || typeId.isBlank()) {
            return null;
        }

        return switch (typeId) {
            case "self", "area" -> resolveEntityPosition(ctx.ref(), ctx);
            case "target" -> resolveEntityPosition(ctx.targetRef(), ctx);
            case "lookRaycast" -> {
                TargetingResult result = TargetingResolver.resolve(targetingSpec, ctx);
                yield resolveEntityPosition(result.first(), ctx);
            }
            case "areaAtLook" -> resolveLookPosition(ctx, targetingSpec, 24.0);
            case "lookPosition" -> resolveLookPosition(ctx, targetingSpec, DEFAULT_TELEPORT_RANGE);
            case "projectile" -> {
                Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
                yield look != null ? new Vector3d(look.getPosition()) : null;
            }
            default -> null;
        };
    }

    private static Vector3d resolveLookPosition(SkillRuntimeContext ctx,
                                                Map<String, Object> targetingSpec,
                                                double defaultRange) {
        Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
        if (look == null) {
            return resolveEntityPosition(ctx.ref(), ctx);
        }
        double maxRange = targetingSpec.get("maxRange") instanceof Number r ? r.doubleValue() : defaultRange;
        return new Vector3d(look.getPosition()).addScaled(look.getDirection(), maxRange);
    }

    private static Vector3d resolveEntityPosition(Ref<EntityStore> entityRef, SkillRuntimeContext ctx) {
        if (entityRef == null) {
            return null;
        }
        TransformComponent transform = (TransformComponent) ctx.store().getComponent(
                entityRef, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
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
