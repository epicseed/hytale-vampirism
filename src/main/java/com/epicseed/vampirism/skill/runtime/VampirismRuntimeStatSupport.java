package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.runtime.stats.RuntimeStatSupport;
import com.epicseed.epiccore.skill.runtime.stats.TypedRuntimeStatSupport;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.hypixel.hytale.logger.HytaleLogger;

final class VampirismRuntimeStatSupport {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    static final RuntimeStatSupport<SkillRuntimeContext> RUNTIME = TypedRuntimeStatSupport.create(
            VampireStatType.class,
            VampirismRuntimeStatSupport::resolveStatType,
            (statType, fallback, context) -> ModifierContext.REGISTRY.compute(statType, fallback, context.modifierContext()));

    static final RuntimeStatSupport<ModifierContext> MODIFIER = TypedRuntimeStatSupport.create(
            VampireStatType.class,
            VampirismRuntimeStatSupport::resolveStatType,
            (statType, fallback, context) -> ModifierContext.REGISTRY.compute(statType, fallback, context));

    private VampirismRuntimeStatSupport() {
    }

    @Nullable
    static VampireStatType resolveStatType(@Nullable String statId) {
        if (statId == null || statId.isBlank()) {
            return null;
        }
        try {
            return VampireStatType.valueOf(statId);
        } catch (IllegalArgumentException e) {
            LOGGER.atWarning().log("[VampirismRuntimeStatSupport] Unknown stat id '" + statId + "'");
            return null;
        }
    }
}
