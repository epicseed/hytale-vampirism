package com.epicseed.vampirism.skill.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.epicseed.epiccore.skill.data.SkillRuntimeBindingsLoadHooks;
import com.epicseed.epiccore.skill.runtime.SkillDefinitionCatalog;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeBindings;
import com.epicseed.epiccore.skill.runtime.SkillRuntimeBindingsHolder;
import org.junit.jupiter.api.Test;

class SkillLoaderHooksTest {

    @Test
    void applyRuntimeBindingsReplacesAndClearsScopedSnapshot() {
        SkillRuntimeBindingsHolder runtimeBindings = new SkillRuntimeBindingsHolder();
        SkillRuntimeBindingsLoadHooks hooks = new SkillRuntimeBindingsLoadHooks(runtimeBindings);
        SkillRuntimeBindings bindings = SkillRuntimeBindings.of(
                java.util.Map.of("IS_IN_BAT_FORM", "Potion_Morph_Bat"),
                java.util.Map.of("primary", "BloodSucker"));

        hooks.applyRuntimeBindings(bindings);
        assertSame(bindings, runtimeBindings.snapshot());

        hooks.applyRuntimeBindings(null);
        assertSame(SkillRuntimeBindings.empty(), runtimeBindings.snapshot());
    }

    @Test
    void loadDefinitionsAppliesBindingsThroughHooks() {
        SkillRuntimeBindingsHolder runtimeBindings = new SkillRuntimeBindingsHolder();
        SkillRuntimeBindingsLoadHooks hooks = new SkillRuntimeBindingsLoadHooks(runtimeBindings);
        SkillLoader loader = new SkillLoader(SkillDataPaths.vampirismDefaults(), hooks);
        SkillDefinitionCatalog catalog = new SkillDefinitionCatalog();

        loader.loadDefinitions(catalog.registries());

        SkillRuntimeBindings bindings = runtimeBindings.snapshot();
        assertNotNull(bindings);
        assertEquals("Potion_Morph_AncientBat", bindings.effectIdFor("IS_IN_ANCIENT_FORM"));
        assertEquals("BloodSucker", bindings.abilityFor("primary"));
    }
}
