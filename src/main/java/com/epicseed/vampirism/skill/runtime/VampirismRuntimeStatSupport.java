package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nullable;

import com.epicseed.epiccore.modifier.StatType;
import com.epicseed.epiccore.skill.runtime.stats.RuntimeStatSupport;
import com.epicseed.vampirism.modifier.ModifierContext;
import com.epicseed.vampirism.modifier.VampireStatType;
import com.hypixel.hytale.logger.HytaleLogger;

final class VampirismRuntimeStatSupport {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    static final RuntimeStatSupport<SkillRuntimeContext> RUNTIME = new RuntimeStatSupport<>() {
        @Override
        public @Nullable StatType resolveStatKey(String statId) {
            return resolveStatType(statId);
        }

        @Override
        public float resolveStatValue(StatType statKey, float fallback, SkillRuntimeContext context) {
            if (!(statKey instanceof VampireStatType statType)) {
                return fallback;
            }
            return ModifierContext.REGISTRY.compute(statType, fallback, context.modifierContext());
        }
    };

    static final RuntimeStatSupport<ModifierContext> MODIFIER = new RuntimeStatSupport<>() {
        @Override
        public @Nullable StatType resolveStatKey(String statId) {
            return resolveStatType(statId);
        }

        @Override
        public float resolveStatValue(StatType statKey, float fallback, ModifierContext context) {
            if (!(statKey instanceof VampireStatType statType)) {
                return fallback;
            }
            return ModifierContext.REGISTRY.compute(statType, fallback, context);
        }
    };

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
