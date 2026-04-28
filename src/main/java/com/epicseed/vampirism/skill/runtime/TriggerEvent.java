package com.epicseed.vampirism.skill.runtime;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable event fired when a game-play moment that skills/passives can react to occurs.
 *
 * <p>Create events with the factory methods to ensure a consistent payload structure.
 * Pass the event to {@link TriggerDispatcher#dispatch(TriggerEvent)} to run all matching
 * passive/skill trigger handlers for the acting player.
 *
 * <h3>Known trigger types</h3>
 * <ul>
 *   <li>{@link #ON_ACTIVATE} – an ability or effect was activated.</li>
 *   <li>{@link #ON_KILL} – the player killed an entity.</li>
 *   <li>{@link #ON_FEED} – the player fed (blood drain) successfully.</li>
 *   <li>{@link #ON_DAMAGE_TAKEN} – the player took damage.</li>
 *   <li>{@link #ON_DAMAGE_DEALT} – the player dealt damage.</li>
 *   <li>{@link #ON_TICK} – periodic tick (use sparingly).</li>
 * </ul>
 */
public final class TriggerEvent {

    public static final String ON_ACTIVATE     = "onActivate";
    public static final String ON_KILL         = "onKill";
    public static final String ON_FEED         = "onFeed";
    public static final String ON_FIRST_HIT    = "onFirstHit";
    public static final String ON_DAMAGE_TAKEN = "onDamageTaken";
    public static final String ON_DAMAGE_DEALT = "onDamageDealt";
    public static final String ON_TICK         = "onTick";
    /** Fired once when a player connects; used to trigger connect-time passive effects. */
    public static final String ON_CONNECT      = "onConnect";
    /** Fired when the caster's HP% crosses below a configured threshold (default 0.3). */
    public static final String ON_LOW_HEALTH   = "onLowHealth";
    /** Fired when the caster breaks a block (requires an ECS listener to invoke). */
    public static final String ON_BLOCK_BREAK  = "onBlockBreak";

    private final String type;
    private final SkillRuntimeContext context;
    private final Map<String, Object> payload;

    private TriggerEvent(@Nonnull String type,
                         @Nonnull SkillRuntimeContext context,
                         @Nonnull Map<String, Object> payload) {
        this.type = type;
        this.context = context;
        this.payload = payload;
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /** Creates a trigger event with no extra payload. */
    @Nonnull
    public static TriggerEvent of(@Nonnull String type, @Nonnull SkillRuntimeContext context) {
        return new TriggerEvent(type, context, Collections.emptyMap());
    }

    /** Creates a trigger event with an arbitrary payload map. */
    @Nonnull
    public static TriggerEvent of(@Nonnull String type,
                                  @Nonnull SkillRuntimeContext context,
                                  @Nonnull Map<String, Object> payload) {
        return new TriggerEvent(type, context, Map.copyOf(payload));
    }

    @Nonnull
    public static TriggerEvent onActivate(@Nonnull SkillRuntimeContext context, @Nullable String abilityId) {
        Map<String, Object> payload = abilityId != null ? Map.of("abilityId", abilityId) : Collections.emptyMap();
        return new TriggerEvent(ON_ACTIVATE, context, payload);
    }

    @Nonnull
    public static TriggerEvent onKill(@Nonnull SkillRuntimeContext context) {
        return of(ON_KILL, context);
    }

    @Nonnull
    public static TriggerEvent onFeed(@Nonnull SkillRuntimeContext context) {
        return of(ON_FEED, context);
    }

    @Nonnull
    public static TriggerEvent onFirstHit(@Nonnull SkillRuntimeContext context) {
        return of(ON_FIRST_HIT, context);
    }

    @Nonnull
    public static TriggerEvent onConnect(@Nonnull SkillRuntimeContext context) {
        return of(ON_CONNECT, context);
    }

    @Nonnull
    public static TriggerEvent onDamageTaken(@Nonnull SkillRuntimeContext context, float amount) {
        return new TriggerEvent(ON_DAMAGE_TAKEN, context, Map.of("amount", amount));
    }

    @Nonnull
    public static TriggerEvent onDamageDealt(@Nonnull SkillRuntimeContext context, float amount) {
        return new TriggerEvent(ON_DAMAGE_DEALT, context, Map.of("amount", amount));
    }

    @Nonnull
    public static TriggerEvent onLowHealth(@Nonnull SkillRuntimeContext context, float hpPercent) {
        return new TriggerEvent(ON_LOW_HEALTH, context, Map.of("hpPercent", hpPercent));
    }

    @Nonnull
    public static TriggerEvent onBlockBreak(@Nonnull SkillRuntimeContext context, @Nullable String blockId) {
        Map<String, Object> payload = blockId != null ? Map.of("blockId", blockId) : Collections.emptyMap();
        return new TriggerEvent(ON_BLOCK_BREAK, context, payload);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** The trigger type identifier (e.g. "onKill"). */
    @Nonnull
    public String type() { return type; }

    /** The runtime context of the acting player. */
    @Nonnull
    public SkillRuntimeContext context() { return context; }

    /** Optional extra data attached to this event (e.g. damage amount). */
    @Nonnull
    public Map<String, Object> payload() { return payload; }

    @Nullable
    public Object payloadValue(String key) { return payload.get(key); }

    @Override
    public String toString() {
        return "TriggerEvent{type='" + type + "', uuid=" + context.uuid() + "}";
    }
}
