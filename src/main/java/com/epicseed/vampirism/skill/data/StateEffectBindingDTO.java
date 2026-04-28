package com.epicseed.vampirism.skill.data;

/**
 * DTO entry for {@code stateRegistry.json}: maps a {@code stateId} to the
 * Hytale effect id whose presence on the caster signals that state is active.
 *
 * <p>Example JSON:
 * <pre>{"stateId":"IS_IN_ANCIENT_FORM","effectId":"Vampirism/Potion_Morph_AncientBat"}</pre>
 */
public class StateEffectBindingDTO {
    public String stateId;
    public String effectId;
}
