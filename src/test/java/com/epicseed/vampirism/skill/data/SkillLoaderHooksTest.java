package com.epicseed.vampirism.skill.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.epicseed.epiccore.skill.data.SkillDataLoadHooks;
import com.epicseed.vampirism.skill.registry.AbilityRegistry;
import com.epicseed.vampirism.skill.registry.EffectDefRegistry;
import com.epicseed.vampirism.skill.registry.ModifierDefRegistry;
import com.epicseed.vampirism.skill.registry.PassiveRegistry;
import com.epicseed.vampirism.skill.registry.ReusableDefRegistry;
import com.epicseed.vampirism.skill.registry.SkillRegistry;
import com.epicseed.vampirism.skill.registry.StateRegistry;
import com.epicseed.vampirism.skill.registry.StatDefRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SkillLoaderHooksTest {

    @Test
    void loadDefinitionsAppliesBindingsThroughHooks() {
        CapturingHooks hooks = new CapturingHooks();
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

        assertNotNull(hooks.stateEffectBindings);
        assertNotNull(hooks.abilitySlotBindings);
        assertEquals("Potion_Morph_AncientBat", hooks.stateEffectBindings.get("IS_IN_ANCIENT_FORM"));
        assertEquals("BloodSucker", hooks.abilitySlotBindings.get("primary"));
    }

    private static final class CapturingHooks implements SkillDataLoadHooks {

        private Map<String, String> stateEffectBindings;
        private Map<String, String> abilitySlotBindings;

        @Override
        public void applyStateEffectBindings(Map<String, String> stateEffectBindings) {
            this.stateEffectBindings = new LinkedHashMap<>(stateEffectBindings);
        }

        @Override
        public void applyAbilitySlotBindings(Map<String, String> abilitySlotBindings) {
            this.abilitySlotBindings = new LinkedHashMap<>(abilitySlotBindings);
        }
    }
}
