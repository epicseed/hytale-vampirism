package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.hytale.ProjectileAdapter;
import com.epicseed.vampirism.modifier.ModifierRegistry;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.util.TargetUtil;

public final class ProjectileActionHandler {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ProjectileActionHandler() {
    }

    public static boolean spawnProjectile(Map<String, Object> action, SkillRuntimeContext ctx) {
        Object projectileIdValue = action.get("projectileId");
        if (!(projectileIdValue instanceof String projectileId) || projectileId.isBlank()) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile missing projectileId: " + action);
            return false;
        }
        UUID ownerUuid = ProjectileAdapter.extractEntityUuid(ctx.ref(), ctx.store());
        if (ownerUuid == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile requires UUIDComponent-backed entity context");
            return false;
        }
        Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
        if (look == null) {
            LOGGER.atWarning().log("[SkillActionExecutor] spawnProjectile: failed to resolve look transform");
            return false;
        }

        ProjectileAdapter.SpawnedProjectile spawned = ProjectileAdapter.assemble(ctx.store(), projectileId, look);
        if (spawned == null) {
            return false;
        }

        ProjectileAdapter.shoot(spawned, ownerUuid, look);
        tuneProjectileFromStats(spawned, ctx, action);
        ProjectileAdapter.addEntity(ctx.store(), spawned);
        LOGGER.atInfo().log("[SkillActionExecutor] spawnProjectile: launched " + projectileId);
        return true;
    }

    private static void tuneProjectileFromStats(@Nonnull ProjectileAdapter.SpawnedProjectile spawned,
                                                @Nonnull SkillRuntimeContext ctx,
                                                @Nonnull Map<String, Object> action) {
        VampireStatType damageStat = resolveStatType(action.get("damageStatId"), VampireStatType.PROJECTILE_DAMAGE);
        VampireStatType speedStat = resolveStatType(action.get("speedStatId"), VampireStatType.PROJECTILE_SPEED);

        float baseDamage = ProjectileAdapter.baseDamage(spawned.projectile());
        if (baseDamage > 0f) {
            float tunedDamage = ModifierRegistry.get().compute(damageStat, baseDamage, ctx.modifierContext());
            ProjectileAdapter.setDamageMultiplier(spawned.projectile(), tunedDamage / baseDamage);
        }

        float speedMultiplier = Math.max(0f, ModifierRegistry.get().compute(
                speedStat, 1f, ctx.modifierContext()));
        ProjectileAdapter.scaleVelocity(spawned, speedMultiplier);
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
}
