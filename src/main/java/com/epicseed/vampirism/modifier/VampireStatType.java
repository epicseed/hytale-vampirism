package com.epicseed.vampirism.modifier;

/**
 * Built-in vampire stat types. Systems call
 * {@link ModifierRegistry#compute} with one of these values.
 *
 * <p>Priority convention for global modifiers:
 * <ul>
 *   <li>0–99:   base state (sunlight override, bloodlust, night)</li>
 *   <li>100–199: skill bonuses</li>
 *   <li>200–299: active effects</li>
 *   <li>300+:    relic / temporary buffs</li>
 * </ul>
 */
public enum VampireStatType implements StatType {

    /** Outgoing damage multiplier (base = 1.0). */
    DAMAGE_OUT,

    /** Incoming damage reduction fraction (base = 0.0). */
    DAMAGE_IN_REDUCTION,

    /** Lifesteal fraction applied to dealt damage (base = 0.0). */
    LIFESTEAL,

    /** Flat health restored on hit (base = 0.0). */
    ON_HIT_HEAL,

    /** Bonus multiplier for ambush / backstab damage (base = 0.0). */
    AMBUSH_DAMAGE_MULTIPLIER,

    /** Base projectile damage for blood abilities (base = config/runtime dependent). */
    PROJECTILE_DAMAGE,

    /** Target health threshold (0.0-1.0) used by execute-style abilities (base = disabled). */
    ABILITY_EXECUTE_HEALTH_THRESHOLD,

    /** Multiplier applied to ability self-damage components (base = 1.0). */
    SELF_DAMAGE_MULTIPLIER,

    /** Frenzy stamina recovery fraction (base = 0.0). */
    FRENZY_STAMINA_RECOVERY,

    /** Movement speed (base set by day/night config). */
    SPEED,

    /** Movement speed while in bat form. */
    BAT_FORM_SPEED,

    /** Movement speed while in ancient form. */
    ANCIENT_FORM_SPEED,

    /** Projectile travel speed. */
    PROJECTILE_SPEED,

    /** Temporary speed boost granted after a kill. */
    KILL_SPEED_BOOST,

    /** Duration of the post-kill speed boost. */
    KILL_SPEED_BOOST_DURATION,

    /** Fall damage reduction fraction (base = 0.0). */
    FALL_DAMAGE_REDUCTION,

    /** Resistance to sunburn tier escalation (base = 0.0). */
    SUNBURN_RESISTANCE,

    /** Passive blood regeneration per second (base = 0.0). */
    BLOOD_REGEN,

    /** Multiplier applied to all healing the vampire receives (base = 1.0). */
    HEALING_RECEIVED,

    /** Flat health restored after feeding. */
    BITE_HEAL_AMOUNT,

    /** Max health while in bat form. */
    BAT_FORM_MAX_HEALTH,

    /** Bonus health while in ancient form. */
    ANCIENT_FORM_HEALTH_BONUS,

    /** Multiplier applied to max blood capacity. */
    BLOOD_BAR_CAPACITY,

    /** Multiplier applied to blood drain / consumption. */
    BLOOD_DRAIN_RATE,

    /** Multiplier applied to blood collection gains. */
    BLOOD_COLLECTION_RATE,

    /** Multiplier applied to feeding speed. */
    FEEDING_SPEED,

    /** Multiplier applied to ability blood costs. */
    ABILITY_BLOOD_COST_MULTIPLIER,

    /** Fractional cooldown reduction for abilities. */
    ABILITY_COOLDOWN_REDUCTION,

    /** Fixed cooldown override in seconds for a targeted ability (0 = disabled). */
    ABILITY_COOLDOWN_OVERRIDE_SECONDS,

    /** Multiplier applied to timed ability durations. */
    ABILITY_DURATION_MULTIPLIER,

    /** Multiplier applied to stealth detection radius. */
    STEALTH_DETECTION_RADIUS,
}
