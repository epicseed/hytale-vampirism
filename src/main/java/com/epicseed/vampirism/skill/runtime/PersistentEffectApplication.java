package com.epicseed.vampirism.skill.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record PersistentEffectApplication(@Nonnull PersistentPassiveOwnerKey ownerKey,
                                          @Nonnull String effectId,
                                          @Nullable String targetingId,
                                          long appliedAtMs) {

    public PersistentEffectApplication {
        if (effectId == null || effectId.isBlank()) {
            throw new IllegalArgumentException("effectId is required");
        }
    }

    public Map<String, Object> toRemoveAction() {
        Map<String, Object> removeSpec = new LinkedHashMap<>();
        removeSpec.put("type", "removeEffect");
        removeSpec.put("effectId", effectId);
        if (targetingId != null && !targetingId.isBlank()) {
            removeSpec.put("targetingId", targetingId);
        }
        return removeSpec;
    }
}
