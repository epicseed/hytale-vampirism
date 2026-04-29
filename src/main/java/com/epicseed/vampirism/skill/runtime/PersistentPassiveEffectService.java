package com.epicseed.vampirism.skill.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;

public final class PersistentPassiveEffectService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final float PERSISTENT_REAPPLY_INTERVAL_S = 1.0f;
    private static final Map<UUID, PersistentPassiveState> states = new ConcurrentHashMap<>();

    private PersistentPassiveEffectService() {
    }

    public static boolean isPersistentOwner(List<Map<String, Object>> triggers,
                                            List<Map<String, Object>> actions) {
        return actions != null && !actions.isEmpty() && (triggers == null || triggers.isEmpty());
    }

    public static void registerPersistentOwner(@Nonnull Set<String> activeOwnerKeys,
                                               @Nonnull PersistentPassiveOwnerKey ownerKey,
                                               List<Map<String, Object>> triggers,
                                               List<Map<String, Object>> actions) {
        if (isPersistentOwner(triggers, actions)) {
            activeOwnerKeys.add(ownerKey.serialized());
        }
    }

    public static void handleOwner(@Nonnull PersistentPassiveOwnerKey ownerKey,
                                   List<Map<String, Object>> actions,
                                   @Nonnull SkillRuntimeContext ctx) {
        UUID uuid = ctx.uuid();
        if (uuid == null || actions == null || actions.isEmpty()) return;

        PersistentPassiveState state = states.computeIfAbsent(uuid, ignored -> new PersistentPassiveState());
        long nowMs = System.currentTimeMillis();
        if (nowMs - state.lastApplyMs(ownerKey) < (long)(PERSISTENT_REAPPLY_INTERVAL_S * 1000L)) return;

        boolean applied = SkillActionExecutor.executeAll(actions, ctx);
        if (!applied) return;

        state.recordApply(ownerKey, nowMs, collectApplications(ownerKey, actions, nowMs));
        LOGGER.atFine().log("[PersistentPassiveEffectService] Applied persistent "
                + ownerKey.ownerType() + " '" + ownerKey.ownerId() + "' for " + uuid);
    }

    public static void cleanupInactiveOwners(@Nonnull UUID uuid,
                                             @Nonnull SkillRuntimeContext ctx,
                                             @Nonnull Set<String> activeOwnerKeys) {
        PersistentPassiveState state = states.get(uuid);
        if (state == null || state.isEmpty()) return;

        Set<String> staleOwnerKeys = new HashSet<>(state.ownerKeys());
        staleOwnerKeys.removeAll(activeOwnerKeys);
        if (staleOwnerKeys.isEmpty()) return;

        for (String ownerKey : staleOwnerKeys) {
            List<PersistentEffectApplication> applications = state.removeApplications(ownerKey);
            if (applications == null || applications.isEmpty()) continue;

            for (PersistentEffectApplication application : applications) {
                SkillActionExecutor.execute(application.toRemoveAction(), ctx);
            }
        }

        if (state.isEmpty()) {
            states.remove(uuid);
        }
    }

    public static void clearPlayer(@Nonnull UUID uuid) {
        states.remove(uuid);
    }

    private static List<PersistentEffectApplication> collectApplications(@Nonnull PersistentPassiveOwnerKey ownerKey,
                                                                        @Nonnull List<Map<String, Object>> actions,
                                                                        long appliedAtMs) {
        if (actions.isEmpty()) return Collections.emptyList();

        List<PersistentEffectApplication> applications = new ArrayList<>();
        for (Map<String, Object> actionSpec : actions) {
            Map<String, Object> resolved = SkillRuntimeDefinitions.resolveAction(actionSpec);
            Object type = resolved.get("type");
            if (!"applyEffect".equals(type)) continue;

            Object effectId = resolved.get("effectId");
            if (!(effectId instanceof String effectIdString) || effectIdString.isBlank()) continue;

            String targetingId = resolved.get("targetingId") instanceof String targetingIdString
                    && !targetingIdString.isBlank()
                    ? targetingIdString
                    : null;
            applications.add(new PersistentEffectApplication(ownerKey, effectIdString, targetingId, appliedAtMs));
        }
        return applications;
    }
}
