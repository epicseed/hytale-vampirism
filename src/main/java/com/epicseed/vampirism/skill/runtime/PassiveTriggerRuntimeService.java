package com.epicseed.vampirism.skill.runtime;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;

public final class PassiveTriggerRuntimeService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final Map<UUID, Map<String, Long>> triggerLastFire = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> connectInitialized = new ConcurrentHashMap<>();

    private PassiveTriggerRuntimeService() {
    }

    public static void initializePlayerSession(@Nonnull SkillRuntimeContext ctx) {
        UUID uuid = ctx.uuid();
        if (uuid == null) return;
        if (connectInitialized.putIfAbsent(uuid, Boolean.TRUE) != null) return;
        TriggerDispatcher.dispatch(TriggerEvent.onConnect(ctx));
    }

    public static void handleOwner(@Nonnull PersistentPassiveOwnerKey ownerKey,
                                   List<Map<String, Object>> triggers,
                                   List<Map<String, Object>> actions,
                                   @Nonnull SkillRuntimeContext ctx) {
        if (actions == null || actions.isEmpty() || triggers == null || triggers.isEmpty()) return;

        for (int i = 0; i < triggers.size(); i++) {
            Map<String, Object> triggerSpec = triggers.get(i);
            Map<String, Object> resolved = SkillRuntimeDefinitions.resolveTrigger(triggerSpec);
            Object typeObj = resolved.get("type");
            if (!(typeObj instanceof String type)) continue;
            if ("onCondition".equals(type)) {
                handleOnConditionTrigger(ownerKey, actions, i, resolved, ctx);
            }
        }
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        triggerLastFire.remove(uuid);
        connectInitialized.remove(uuid);
    }

    @SuppressWarnings("unchecked")
    private static void handleOnConditionTrigger(@Nonnull PersistentPassiveOwnerKey ownerKey,
                                                 @Nonnull List<Map<String, Object>> actions,
                                                 int triggerIndex,
                                                 @Nonnull Map<String, Object> resolved,
                                                 @Nonnull SkillRuntimeContext ctx) {
        UUID uuid = ctx.uuid();
        if (uuid == null) return;

        String conditionId = resolved.get("conditionId") instanceof String s ? s : null;
        boolean conditionMet;
        if (conditionId != null) {
            conditionMet = SkillConditionEvaluator.evaluate(Map.of("conditionId", conditionId), ctx);
        } else {
            Object inlineConds = resolved.get("conditions");
            if (inlineConds instanceof List<?> list) {
                conditionMet = SkillConditionEvaluator.evaluateAll((List<Map<String, Object>>) list, ctx);
            } else {
                conditionMet = false;
            }
        }

        if (!conditionMet) return;

        double cooldownSec = resolved.get("cooldown") instanceof Number n ? n.doubleValue() : 0.0;
        String cooldownKey = ownerKey.serialized() + "#" + triggerIndex;
        Map<String, Long> playerFires = triggerLastFire.computeIfAbsent(uuid, ignored -> new ConcurrentHashMap<>());
        long lastFire = playerFires.getOrDefault(cooldownKey, 0L);
        long nowMs = System.currentTimeMillis();

        if (cooldownSec > 0.0 && (nowMs - lastFire) < (long)(cooldownSec * 1000L)) return;

        playerFires.put(cooldownKey, nowMs);
        LOGGER.atInfo().log("[PassiveTriggerRuntimeService] onCondition fired for "
                + ownerKey.ownerType() + "='" + ownerKey.ownerId() + "' trigger=" + triggerIndex
                + " player=" + uuid);
        SkillActionExecutor.executeAll(actions, ctx);
    }
}
