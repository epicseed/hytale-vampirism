package com.epicseed.vampirism.skill.runtime;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.epicseed.epiccore.skill.progression.ProgressionDefinitionProvider;
import com.epicseed.epiccore.skill.progression.SkillProgressionAccess;
import com.epicseed.epiccore.skill.runtime.passive.TriggerEvent;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

public final class PassiveService extends com.epicseed.epiccore.skill.runtime.passive.PassiveService<SkillRuntimeContext> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static PassiveService instance;

    public static PassiveService init(@Nonnull ProgressionDefinitionProvider definitionProvider,
                                      @Nonnull SkillProgressionAccess progressionAccess,
                                      @Nonnull java.util.function.Consumer<TriggerEvent<SkillRuntimeContext>> triggerDispatcher) {
        instance = new PassiveService(definitionProvider, progressionAccess, triggerDispatcher);
        return instance;
    }

    @Nonnull
    public static PassiveService get() {
        if (instance == null) throw new IllegalStateException("PassiveService not initialized!");
        return instance;
    }

    public PassiveService(@Nonnull ProgressionDefinitionProvider definitionProvider,
                          @Nonnull SkillProgressionAccess progressionAccess,
                          @Nonnull java.util.function.Consumer<TriggerEvent<SkillRuntimeContext>> triggerDispatcher) {
        super(definitionProvider, progressionAccess, triggerDispatcher);
        instance = this;
        LOGGER.atInfo().log("[PassiveService] Initialized.");
    }

    /** Default HP% threshold used when a trigger spec does not override it. */
    private static final float DEFAULT_LOW_HP_THRESHOLD = 0.30f;

    /** Remembers each player's last observed HP% so we only dispatch on crossings. */
    private final java.util.Map<UUID, Float> lastHpPercent = new ConcurrentHashMap<>();

    @Override
    protected void afterDamageTaken(SkillRuntimeContext ctx, float amount) {
        checkLowHealth(ctx);
    }

    public void checkLowHealth(@Nonnull SkillRuntimeContext ctx) {
        UUID uuid = ctx.uuid();
        if (uuid == null) return;
        EntityStatMap stats = (EntityStatMap) ctx.store().getComponent(ctx.ref(), EntityStatMap.getComponentType());
        if (stats == null) return;
        EntityStatValue health = stats.get(DefaultEntityStatTypes.getHealth());
        if (health == null || health.getMax() <= 0f) return;
        float pct = health.get() / health.getMax();
        Float last = lastHpPercent.put(uuid, pct);
        float previous = last != null ? last : 1f;
        if (pct <= DEFAULT_LOW_HP_THRESHOLD && previous > DEFAULT_LOW_HP_THRESHOLD) {
            dispatch(TriggerEvent.onLowHealth(ctx, pct));
        }
    }
}
