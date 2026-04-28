package com.epicseed.vampirism.domain.blood;

import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;
import com.epicseed.vampirism.systems.BloodFeedSystem;

public final class FeedService {
    private FeedService() {
    }

    public static boolean startChannel(@Nonnull SkillRuntimeContext ctx, @Nonnull Map<String, Object> action) {
        return BloodFeedSystem.startChannel(ctx, action);
    }

    public static void clearPlayer(@Nullable UUID uuid) {
        BloodFeedSystem.clearPlayer(uuid);
    }
}
