package com.epicseed.vampirism.skill.runtime.actions;

import java.util.Map;

import com.epicseed.vampirism.domain.blood.BloodConversionService;
import com.epicseed.vampirism.domain.blood.FeedService;
import com.epicseed.vampirism.skill.runtime.SkillRuntimeContext;

public final class ChannelActionHandlers {
    private ChannelActionHandlers() {
    }

    public static boolean startFeedChannel(Map<String, Object> action, SkillRuntimeContext ctx) {
        return FeedService.startChannel(ctx, action);
    }

    public static boolean startHealthToBloodChannel(Map<String, Object> action, SkillRuntimeContext ctx) {
        return BloodConversionService.startChannel(ctx, action);
    }
}
