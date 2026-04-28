package com.epicseed.vampirism.skill.runtime;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Resolves an ability's {@code targeting} spec into a {@link TargetingResult}.
 *
 * <p>Supported targeting types:
 * <ul>
 *   <li>{@code self} – targets the caster (ctx.ref()).</li>
 *   <li>{@code projectile} – targetless cast that fires from the caster's look transform.</li>
 *   <li>{@code target} – targets ctx.targetRef() when present.</li>
 *   <li>{@code area} – queries {@link EntityRefTracker} for all known player refs and filters
 *       by distance from the caster.  Callers no longer need to supply {@code candidateEntities}
 *       but the legacy overload is kept for backwards compatibility.</li>
 *   <li>{@code lookPosition} – reserved for position-based targeting (teleport).  Currently
 *       returns {@code TargetingResult.empty()}; the {@code teleport} action type resolves
 *       the look position directly from {@link TargetUtil#getLook(Ref, com.hypixel.hytale.component.ComponentAccessor)}
 *       and performs its own world raycast / safe-position search.</li>
 * </ul>
 *
 * <p>All types honour an optional {@code team} filter ({@code "self"}, {@code "enemy"},
 * {@code "ally"}, {@code "any"}).  Only {@code "self"} and {@code "any"} are currently
 * evaluated server-side; team-based filtering for enemies/allies is left to callers that
 * have access to the necessary registry data.
 */
public final class TargetingResolver {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TargetingResolver() {}

    /**
     * Resolves targeting without candidate entities.
     * For {@code area} targeting, candidates are sourced automatically from {@link EntityRefTracker}.
     */
    public static TargetingResult resolve(Map<String, Object> targeting, SkillRuntimeContext ctx) {
        return resolve(targeting, ctx, Collections.emptyList());
    }

    /**
     * Resolves targeting with an optional pool of candidate entities.
     * For {@code area} targeting the provided list takes precedence; if empty, falls back to
     * {@link EntityRefTracker} which is populated each tick by player-iterating systems.
     *
     * @param targeting         raw targeting spec (may contain a {@code targetingId} ref)
     * @param ctx               runtime context for the caster
     * @param candidateEntities optional candidate entity refs; may be empty
     */
    public static TargetingResult resolve(Map<String, Object> targeting,
                                          SkillRuntimeContext ctx,
                                          List<Ref<EntityStore>> candidateEntities) {
        if (targeting == null || targeting.isEmpty()) {
            return TargetingResult.of(ctx.ref());
        }

        Map<String, Object> resolved = SkillRuntimeDefinitions.resolveTargeting(targeting);
        Object typeObj = resolved.get("type");
        if (!(typeObj instanceof String typeId) || typeId.isBlank()) {
            LOGGER.atWarning().log("[TargetingResolver] Targeting spec missing type: " + resolved);
            return TargetingResult.of(ctx.ref());
        }

        return switch (typeId) {
            case "self" -> TargetingResult.of(ctx.ref());

            case "projectile" -> TargetingResult.empty();

            case "target" -> {
                Ref<EntityStore> targetRef = ctx.targetRef();
                if (targetRef == null) {
                    yield TargetingResult.empty();
                }
                yield TargetingResult.of(targetRef);
            }

            case "area" -> resolveArea(resolved, ctx, candidateEntities);

            case "areaAtLook" -> resolveAreaAtLook(resolved, ctx);

            case "lookRaycast" -> resolveLookRaycast(resolved, ctx);

            case "lookPosition" -> {
                // Position-based targeting for teleport: the teleport action resolves the live
                // look transform, performs a block raycast and safe landing search, then applies
                // the move directly in SkillActionExecutor. Return empty here; AbilityService
                // will proceed to the targetless execution branch which calls the teleport action.
                yield TargetingResult.empty();
            }

            default -> {
                LOGGER.atWarning().log("[TargetingResolver] Unsupported targeting type: " + typeId);
                yield TargetingResult.empty();
            }
        };
    }

    private static TargetingResult resolveArea(Map<String, Object> spec,
                                               SkillRuntimeContext ctx,
                                               List<Ref<EntityStore>> candidateEntities) {
        double radius = spec.get("radius") instanceof Number r ? r.doubleValue() : 8.0;

        // Prefer explicitly provided candidates; fall back to the global entity tracker.
        Collection<Ref<EntityStore>> candidates = candidateEntities.isEmpty()
                ? EntityRefTracker.getAll()
                : candidateEntities;

        if (candidates.isEmpty()) {
            return TargetingResult.empty();
        }

        String team = spec.get("team") instanceof String t ? t : "any";
        if ("self".equals(team)) {
            return TargetingResult.of(ctx.ref());
        }

        // Get caster position for distance filtering.
        TransformComponent casterTransform = (TransformComponent) ctx.store().getComponent(
                ctx.ref(), TransformComponent.getComponentType());
        if (casterTransform == null) {
            LOGGER.atWarning().log("[TargetingResolver] area targeting: caster has no TransformComponent.");
            return TargetingResult.empty();
        }
        var casterPos = casterTransform.getPosition();
        double radiusSq = radius * radius;

        List<Ref<EntityStore>> result = new ArrayList<>();
        for (Ref<EntityStore> candidateRef : candidates) {
            // Exclude the caster.
            if (candidateRef.equals(ctx.ref())) continue;

            TransformComponent t = (TransformComponent) ctx.store().getComponent(
                    candidateRef, TransformComponent.getComponentType());
            if (t == null) continue;

            var pos = t.getPosition();
            double dx = pos.x - casterPos.x;
            double dy = pos.y - casterPos.y;
            double dz = pos.z - casterPos.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                result.add(candidateRef);
            }
        }
        return TargetingResult.of(result);
    }

    /**
     * {@code areaAtLook} targeting: queries a sphere of entities centered at the caster's
     * look position (raycast up to {@code maxRange}, default 24) and filtered by {@code radius}.
     */
    private static TargetingResult resolveAreaAtLook(Map<String, Object> spec, SkillRuntimeContext ctx) {
        double radius   = spec.get("radius")   instanceof Number r ? r.doubleValue() : 6.0;
        double maxRange = spec.get("maxRange") instanceof Number r ? r.doubleValue() : 24.0;

        Transform look = TargetUtil.getLook(ctx.ref(), ctx.store());
        if (look == null) {
            LOGGER.atWarning().log("[TargetingResolver] areaAtLook: failed to resolve look transform.");
            return TargetingResult.empty();
        }

        Vector3d origin = look.getPosition();
        Vector3d direction = look.getDirection();
        Vector3d center = origin.addScaled(direction, maxRange);

        List<Ref<EntityStore>> sphere = TargetUtil.getAllEntitiesInSphere(center, radius, ctx.store());
        if (sphere == null || sphere.isEmpty()) return TargetingResult.empty();

        List<Ref<EntityStore>> result = new ArrayList<>(sphere.size());
        for (Ref<EntityStore> r : sphere) {
            if (!r.equals(ctx.ref())) result.add(r);
        }
        return TargetingResult.of(result);
    }

    /**
     * {@code lookRaycast} targeting: returns the single entity directly under the crosshair
     * within {@code maxRange} (default 8). Distinct from {@code target} which uses aim-assist.
     */
    private static TargetingResult resolveLookRaycast(Map<String, Object> spec, SkillRuntimeContext ctx) {
        float maxRange = spec.get("maxRange") instanceof Number r ? r.floatValue() : 8f;
        Ref<EntityStore> hit = TargetUtil.getTargetEntity(ctx.ref(), maxRange, ctx.store());
        if (hit == null) return TargetingResult.empty();
        return TargetingResult.of(hit);
    }
}
