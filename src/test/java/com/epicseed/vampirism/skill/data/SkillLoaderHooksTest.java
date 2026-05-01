package com.epicseed.vampirism.skill.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.epicseed.epiccore.skill.runtime.SkillRuntimeBindings;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeBindingsHolder;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.ModifierDefRegistry;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.ReusableDefRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.registry.StateRegistry;
import com.epicseed.vampirism.skill.registry.StatDefRegistry;
import org.junit.jupiter.api.Test;

class SkillLoaderHooksTest {

    @Test
    void loadDefinitionsAppliesBindingsThroughHooks() {
        SkillRuntimeBindingsHolder runtimeBindings = new SkillRuntimeBindingsHolder();
        VampirismSkillDataLoadHooks hooks = new VampirismSkillDataLoadHooks(runtimeBindings);
        SkillLoader loader = new SkillLoader(SkillDataPaths.vampirismDefaults(), hooks);

        loader.LoadDefinitions(
                new PassiveRegistry(),
                new AbilityRegistry(),
                new ModifierDefRegistry(),
                new EffectDefRegistry(),
                new StateRegistry(),
                new StatDefRegistry(),
                new ReusableDefRegistry(),
                new ReusableDefRegistry(),
                new ReusableDefRegistry(),
                new ReusableDefRegistry(),
                new ReusableDefRegistry());

        SkillRuntimeBindings bindings = runtimeBindings.snapshot();
        assertNotNull(bindings);
        assertEquals("Potion_Morph_AncientBat", bindings.effectIdFor("IS_IN_ANCIENT_FORM"));
        assertEquals("BloodSucker", bindings.abilityFor("primary"));
    }
}
