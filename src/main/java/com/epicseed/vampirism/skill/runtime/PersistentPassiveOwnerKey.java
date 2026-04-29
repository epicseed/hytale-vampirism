package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public record PersistentPassiveOwnerKey(@Nonnull String ownerType, @Nonnull String ownerId) {

    public static PersistentPassiveOwnerKey skill(@Nonnull String skillId) {
        return new PersistentPassiveOwnerKey("skill", skillId);
    }

    public static PersistentPassiveOwnerKey passive(@Nonnull String passiveId) {
        return new PersistentPassiveOwnerKey("passive", passiveId);
    }

    @Nullable
    public static PersistentPassiveOwnerKey parse(@Nullable String value) {
        if (value == null) return null;
        int separator = value.indexOf(':');
        if (separator <= 0 || separator >= value.length() - 1) return null;
        return new PersistentPassiveOwnerKey(value.substring(0, separator), value.substring(separator + 1));
    }

    public PersistentPassiveOwnerKey {
        if (ownerType == null || ownerType.isBlank()) {
            throw new IllegalArgumentException("ownerType is required");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId is required");
        }
    }

    public String serialized() {
        return ownerType + ":" + ownerId;
    }
}
