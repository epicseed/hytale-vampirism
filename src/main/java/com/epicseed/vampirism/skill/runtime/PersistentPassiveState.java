package com.epicseed.vampirism.skill.runtime;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

public final class PersistentPassiveState {

    private final Map<String, Long> lastApplyMsByOwner = new ConcurrentHashMap<>();
    private final Map<String, List<PersistentEffectApplication>> applicationsByOwner = new ConcurrentHashMap<>();

    public long lastApplyMs(@Nonnull PersistentPassiveOwnerKey ownerKey) {
        return lastApplyMsByOwner.getOrDefault(ownerKey.serialized(), 0L);
    }

    public void recordApply(@Nonnull PersistentPassiveOwnerKey ownerKey,
                            long appliedAtMs,
                            @Nonnull List<PersistentEffectApplication> applications) {
        String key = ownerKey.serialized();
        lastApplyMsByOwner.put(key, appliedAtMs);
        applicationsByOwner.put(key, List.copyOf(applications));
    }

    public List<PersistentEffectApplication> removeApplications(@Nonnull String ownerKey) {
        lastApplyMsByOwner.remove(ownerKey);
        return applicationsByOwner.remove(ownerKey);
    }

    public Set<String> ownerKeys() {
        return Collections.unmodifiableSet(lastApplyMsByOwner.keySet());
    }

    public boolean isEmpty() {
        return lastApplyMsByOwner.isEmpty() && applicationsByOwner.isEmpty();
    }
}
