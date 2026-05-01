package com.epicseed.vampirism.domain.blood;
import com.epicseed.vampirism.modifier.ModifierContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeDefinitions;
import com.epicseed.vampirism.systems.VampireInfectionSystem;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.TargetUtil;

public final class ConsumableMarkerService {
    private static final String BLOOD_SUCKER_ABILITY_ID = "BloodSucker";
    private static final String CONSUMABLE_MARKER_EFFECT_ID = "blood_feed_consumable_marker_effect";
    private static final double CONSUMABLE_MARKER_RANGE = 8.0d;
    private static final float CONSUMABLE_MARKER_SCAN_INTERVAL_S = 0.5f;
    private static final float CONSUMABLE_MARKER_DURATION_S = 1.25f;
    private static final float DEFAULT_EXECUTE_THRESHOLD = 0.25f;

    private static final Map<UUID, Float> markerScanAccumulators = new ConcurrentHashMap<>();

    private ConsumableMarkerService() {
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        if (uuid != null) {
            markerScanAccumulators.remove(uuid);
        }
    }

    public static void tick(@Nonnull UUID uuid,
                            @Nonnull Ref<EntityStore> playerRef,
                            float dt,
                            @Nonnull Store<EntityStore> store) {
        if (!PlayerSkillRegistry.get().hasSkill(uuid, BLOOD_SUCKER_ABILITY_ID)
                && !VampireInfectionSystem.allowsTemporaryAbility(uuid, BLOOD_SUCKER_ABILITY_ID)) {
            markerScanAccumulators.remove(uuid);
            return;
        }

        float accumulated = markerScanAccumulators.getOrDefault(uuid, 0f) + Math.max(0f, dt);
        if (accumulated < CONSUMABLE_MARKER_SCAN_INTERVAL_S) {
            markerScanAccumulators.put(uuid, accumulated);
            return;
        }
        markerScanAccumulators.put(uuid, 0f);

        TransformComponent casterTransform = (TransformComponent) store.getComponent(playerRef, TransformComponent.getComponentType());
        if (casterTransform == null) {
            return;
        }

        SkillRuntimeContext ctx = new SkillRuntimeContext(uuid, playerRef, null, store).withActivatedAbility(BLOOD_SUCKER_ABILITY_ID);
        float executeThreshold = resolveThreshold(ctx);
        Vector3d center = new Vector3d(casterTransform.getPosition());

        var nearby = TargetUtil.getAllEntitiesInSphere(center, CONSUMABLE_MARKER_RANGE, store);
        if (nearby == null || nearby.isEmpty()) {
            return;
        }

        for (Ref<EntityStore> targetRef : nearby) {
            if (!FeedEligibility.isConsumableMarkerCandidate(playerRef, targetRef, executeThreshold, store)) {
                continue;
            }
            FeedChannelPresentationService.applyTimedEffect(
                    targetRef, CONSUMABLE_MARKER_EFFECT_ID, CONSUMABLE_MARKER_DURATION_S, OverlapBehavior.EXTEND, store);
        }
    }

    private static float resolveThreshold(@Nonnull SkillRuntimeContext ctx) {
        Ability ability = resolveAbility(BLOOD_SUCKER_ABILITY_ID);
        Map<String, Object> action = resolveFeedAction(ability);
        return ModifierContext.REGISTRY.compute(
                resolveStatName(action.get("executeThresholdStatId"), VampireStatType.ABILITY_EXECUTE_HEALTH_THRESHOLD),
                action.get("executeThreshold") instanceof Number n ? n.floatValue() : DEFAULT_EXECUTE_THRESHOLD,
                ctx.modifierContext());
    }

    @Nonnull
    private static Map<String, Object> resolveFeedAction(@Nullable Ability ability) {
        if (ability == null || ability.actions == null) {
            return Map.of();
        }
        for (Map<String, Object> actionSpec : ability.actions) {
            Map<String, Object> resolved = SkillRuntimeDefinitions.resolveAction(actionSpec);
            if ("startFeedChannel".equals(resolved.get("type"))) {
                return resolved;
            }
        }
        return Map.of();
    }

    @Nullable
    private static Ability resolveAbility(@Nullable String abilityId) {
        if (abilityId == null || abilityId.isBlank()) return null;
        return Vampirism.getInstance().GetAbilityRegistry().Get(abilityId);
    }

    @Nonnull
    private static VampireStatType resolveStatName(@Nullable Object rawStat, @Nonnull VampireStatType fallback) {
        if (rawStat instanceof String statName && !statName.isBlank()) {
            try {
                return VampireStatType.valueOf(statName);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
