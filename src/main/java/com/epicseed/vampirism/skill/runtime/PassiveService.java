package com.epicseed.vampirism.skill.runtime;

import com.epicseed.vampirism.Vampirism;
import com.epicseed.vampirism.skill.model.Passive;
import com.epicseed.vampirism.skill.model.Skill;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime service for vampire passives.
 *
 * <p>Provides two complementary capabilities:
 * <ol>
 *   <li><b>Always-on passives</b>: static modifiers are managed by
 *       {@link com.epicseed.vampirism.skill.manager.SkillTreeManager}. This service exposes an
 *       enumeration API so any system can query which passives a player owns.</li>
     *   <li><b>Reactive passives</b>: game-play events (kill, first hit, damage dealt/taken, feed)
     *       are channelled through {@link #onKill}, {@link #onFirstHit}, {@link #onDamageDealt},
     *       {@link #onDamageTaken}, and {@link #onFeed}. Each method builds a
     *       {@link TriggerEvent} and routes it through
 *       {@link TriggerDispatcher} so every passive with a matching trigger handler is executed.</li>
 * </ol>
 *
 * <p>Game systems should call the methods on this service rather than constructing
 * {@link TriggerEvent}s directly, keeping passive-trigger dispatch in a single place.
 *
 * <h3>Future triggers</h3>
 * <ul>
 *   <li>{@code onFeed} – ready, but requires a future blood-drain system to call it.</li>
 *   <li>{@code onActivate} – can be called by a future ability-activation service.</li>
 *   <li>{@code onTick} – reserved; wire from a periodic tick if fine-grained per-passive
 *       polling becomes necessary (currently handled via the modifier layer instead).</li>
 * </ul>
 */
public final class PassiveService {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static PassiveService instance;

    private PassiveService() {}

    public static void init() {
        instance = new PassiveService();
        LOGGER.atInfo().log("[PassiveService] Initialized.");
    }

    @Nonnull
    public static PassiveService get() {
        if (instance == null) throw new IllegalStateException("PassiveService not initialized!");
        return instance;
    }

    // -------------------------------------------------------------------------
    // Enumeration API
    // -------------------------------------------------------------------------

    /**
     * Returns all {@link Passive} definitions the player has unlocked through the skill tree.
     * Passives are linked to skills via {@code Skill.passiveId}.
     */
    @Nonnull
    public Collection<Passive> getUnlockedPassives(@Nonnull UUID uuid) {
        Vampirism plugin = Vampirism.getInstance();
        SkillRegistry skillRegistry = plugin.GetSkillRegistry();
        PassiveRegistry passiveRegistry = plugin.GetPassiveRegistry();

        Set<String> unlockedSkillIds = PlayerSkillRegistry.get().getUnlockedSkills(uuid);
        List<Passive> result = new ArrayList<>();
        for (String skillId : unlockedSkillIds) {
            Skill skill = skillRegistry.GetSkill(skillId);
            if (skill == null || skill.passiveId == null || skill.passiveId.isBlank()) continue;
            Passive passive = passiveRegistry.Get(skill.passiveId);
            if (passive != null) result.add(passive);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Returns the IDs of all passives the player has unlocked.
     */
    @Nonnull
    public Set<String> getUnlockedPassiveIds(@Nonnull UUID uuid) {
        Vampirism plugin = Vampirism.getInstance();
        SkillRegistry skillRegistry = plugin.GetSkillRegistry();
        PassiveRegistry passiveRegistry = plugin.GetPassiveRegistry();

        Set<String> unlockedSkillIds = PlayerSkillRegistry.get().getUnlockedSkills(uuid);
        Set<String> passiveIds = new HashSet<>();
        for (String skillId : unlockedSkillIds) {
            Skill skill = skillRegistry.GetSkill(skillId);
            if (skill == null || skill.passiveId == null || skill.passiveId.isBlank()) continue;
            Passive passive = passiveRegistry.Get(skill.passiveId);
            if (passive != null) passiveIds.add(passive.id);
        }
        return Collections.unmodifiableSet(passiveIds);
    }

    /**
     * Returns {@code true} if the player has unlocked the passive with the given ID.
     */
    public boolean hasPassive(@Nonnull UUID uuid, @Nonnull String passiveId) {
        return getUnlockedPassiveIds(uuid).contains(passiveId);
    }

    // -------------------------------------------------------------------------
    // Reactive trigger dispatch
    // -------------------------------------------------------------------------

    /**
     * Dispatches an {@code onKill} trigger for the acting player.
     *
     * <p>Call this when a vampire delivers a killing blow.
     *
     * @param ctx Runtime context for the attacking player (source of the kill).
     */
    public void onKill(@Nonnull SkillRuntimeContext ctx) {
        if (ctx.uuid() == null) return;
        LOGGER.atFine().log("[PassiveService] onKill for " + ctx.uuid());
        TriggerDispatcher.dispatch(TriggerEvent.onKill(ctx));
    }

    /**
     * Dispatches an {@code onDamageDealt} trigger for the attacking player.
     *
     * @param ctx    Runtime context for the attacking player.
     * @param amount Damage dealt (post-modifier value).
     */
    public void onDamageDealt(@Nonnull SkillRuntimeContext ctx, float amount) {
        if (ctx.uuid() == null) return;
        TriggerDispatcher.dispatch(TriggerEvent.onDamageDealt(ctx, amount));
    }

    /**
     * Dispatches an {@code onDamageTaken} trigger for the defending player.
     *
     * @param ctx    Runtime context for the player who received damage.
     * @param amount Damage taken (post-modifier value).
     */
    public void onDamageTaken(@Nonnull SkillRuntimeContext ctx, float amount) {
        if (ctx.uuid() == null) return;
        TriggerDispatcher.dispatch(TriggerEvent.onDamageTaken(ctx, amount));
        checkLowHealth(ctx);
    }

    /** Default HP% threshold used when a trigger spec does not override it. */
    private static final float DEFAULT_LOW_HP_THRESHOLD = 0.30f;

    /** Remembers each player's last observed HP% so we only dispatch on crossings. */
    private final java.util.Map<UUID, Float> lastHpPercent = new ConcurrentHashMap<>();

    /**
     * Dispatches {@link TriggerEvent#ON_LOW_HEALTH} when the player's HP% has just crossed
     * below {@link #DEFAULT_LOW_HP_THRESHOLD}. Call after any HP-changing event
     * (primarily {@link #onDamageTaken(SkillRuntimeContext, float)}).
     */
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
            TriggerDispatcher.dispatch(TriggerEvent.onLowHealth(ctx, pct));
        }
    }

    /**
     * Dispatches {@link TriggerEvent#ON_BLOCK_BREAK} for the acting player.
     *
     * <p>TODO: wire from a Hytale {@code BreakBlockEvent} ECS listener once the listener layer
     * provides a reliable Ref↔UUID mapping for the breaker. Currently only callable manually
     * (e.g. from tests or a future block-break system).  See {@code references/entity-effects.md}.
     */
    public void onBlockBreak(@Nonnull SkillRuntimeContext ctx, @Nullable String blockId) {
        if (ctx.uuid() == null) return;
        TriggerDispatcher.dispatch(TriggerEvent.onBlockBreak(ctx, blockId));
    }

    /**
     * Dispatches an {@code onFeed} trigger for the player who just fed.
     *
     * <p>No game system currently emits blood-drain events; this method is ready to be called
     * by a future feed/blood-drain system once it exists.
     *
     * @param ctx Runtime context for the feeding player.
     */
    public void onFeed(@Nonnull SkillRuntimeContext ctx) {
        if (ctx.uuid() == null) return;
        LOGGER.atFine().log("[PassiveService] onFeed for " + ctx.uuid());
        TriggerDispatcher.dispatch(TriggerEvent.onFeed(ctx));
    }

    public void onFirstHit(@Nonnull SkillRuntimeContext ctx) {
        if (ctx.uuid() == null) return;
        LOGGER.atFine().log("[PassiveService] onFirstHit for " + ctx.uuid());
        TriggerDispatcher.dispatch(TriggerEvent.onFirstHit(ctx));
    }

    /**
     * Dispatches an {@code onConnect} trigger for the player who just connected.
     *
     * <p>This allows passive skills with an {@code onConnect} trigger (e.g. Night Vision) to
     * apply their effects immediately when a session starts.  Call from the connect event
     * handler once the player's entity context is available.
     *
     * @param ctx Runtime context for the connecting player.
     */
    public void onConnect(@Nonnull SkillRuntimeContext ctx) {
        if (ctx.uuid() == null) return;
        LOGGER.atFine().log("[PassiveService] onConnect for " + ctx.uuid());
        TriggerDispatcher.dispatch(TriggerEvent.onConnect(ctx));
    }
}
