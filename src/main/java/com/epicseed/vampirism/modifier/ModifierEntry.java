package com.epicseed.vampirism.modifier;

/**
 * A registered modifier with its identity tag and evaluation priority.
 *
 * <p>Tags identify the source and enable targeted removal:
 * <ul>
 *   <li>{@code "skill:shadowStep"} — registered by SkillTreeManager on unlock</li>
 *   <li>{@code "effect:HolyWeakness"} — registered when the effect is applied</li>
 *   <li>{@code "relic:speedBurst"} — registered during a relic ability</li>
 * </ul>
 *
 * <p>Lower priority runs first. Convention:
 * <ul>
 *   <li>0–99: base state (sunlight, bloodlust, night)</li>
 *   <li>100–199: skills</li>
 *   <li>200–299: active effects</li>
 *   <li>300+: relic / temporary buffs</li>
 * </ul>
 */
public record ModifierEntry(ModifierTag tag, int priority, StatModifier modifier) {}
