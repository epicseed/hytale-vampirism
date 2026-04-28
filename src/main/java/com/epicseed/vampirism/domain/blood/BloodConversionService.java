package com.epicseed.vampirism.domain.blood;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.systems.BloodConversionSystem;

public final class BloodConversionService {
    private BloodConversionService() {
    }

    public static boolean startChannel(@Nonnull SkillRuntimeContext ctx, @Nonnull Map<String, Object> action) {
        return BloodConversionSystem.startChannel(ctx, action);
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        BloodConversionSystem.clearPlayer(uuid);
    }
}
