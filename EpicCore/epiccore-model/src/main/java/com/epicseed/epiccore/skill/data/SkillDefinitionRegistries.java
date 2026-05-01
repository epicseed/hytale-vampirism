package com.epicseed.epiccore.skill.data;

import com.epicseed.epiccore.registry.IdRegistry;
import com.epicseed.epiccore.skill.model.Ability;
import com.epicseed.epiccore.skill.model.EffectDef;
import com.epicseed.epiccore.skill.model.ModifierDef;
import com.epicseed.epiccore.skill.model.Passive;
import com.epicseed.epiccore.skill.model.ReusableDef;
import com.epicseed.epiccore.skill.model.StateDef;

public record SkillDefinitionRegistries<A extends Ability,
        M extends ModifierDef,
        R extends ReusableDef,
        S extends StateDef,
        T extends com.epicseed.epiccore.skill.model.StatDef>(
        IdRegistry<Passive> passiveRegistry,
        IdRegistry<A> abilityRegistry,
        IdRegistry<M> modifierDefRegistry,
        IdRegistry<EffectDef> effectDefRegistry,
        IdRegistry<S> stateRegistry,
        IdRegistry<T> statDefRegistry,
        IdRegistry<R> conditionRegistry,
        IdRegistry<R> requirementRegistry,
        IdRegistry<R> triggerRegistry,
        IdRegistry<R> actionRegistry,
        IdRegistry<R> targetingRegistry) {
}
