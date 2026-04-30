package com.epicseed.vampirism.skill.data;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.epicseed.vampirism.modifier.VampireStatType;
import com.epicseed.vampirism.skill.model.Ability;
import com.epicseed.vampirism.skill.model.ModifierDef;
import com.epicseed.vampirism.skill.model.ReusableDef;
import com.epicseed.vampirism.skill.model.StatDef;
import com.epicseed.vampirism.skill.model.VampireState;
import org.junit.jupiter.api.Test;

class VampirismSkillLoaderModelAdapterTest {

    @Test
    void createsVampirismWrapperTypesAndResolvesKnownStats() {
        VampirismSkillLoaderModelAdapter adapter = new VampirismSkillLoaderModelAdapter();

        assertInstanceOf(Ability.class, adapter.newAbility());
        assertInstanceOf(ModifierDef.class, adapter.newModifierDef());
        assertInstanceOf(ReusableDef.class, adapter.newReusableDef());
        assertInstanceOf(VampireState.class, adapter.newState());
        assertInstanceOf(StatDef.class, adapter.newStatDef());
        assertSame(VampireStatType.ABILITY_COOLDOWN_REDUCTION,
                adapter.resolveStatType("ABILITY_COOLDOWN_REDUCTION"));
        assertNull(adapter.resolveStatType("missing_stat"));
    }
}
