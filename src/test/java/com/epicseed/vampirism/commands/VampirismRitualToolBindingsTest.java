package com.epicseed.vampirism.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

final class VampirismRitualToolBindingsTest {

    @Test
    void primaryBindingKeepsDrawCastBehavior() throws IOException {
        String primary = readResource("/Server/Item/Interactions/Vampirism/VampirismRitualTool_Primary.json");

        assertTrue(primary.contains("\"Type\": \"Command\""));
        assertTrue(primary.contains("\"Command\": \"vampirismritual primary\""));
        assertTrue(primary.contains("\"Type\": \"Charging\""));
        assertTrue(primary.contains("\"AllowIndefiniteHold\": true"));
        assertTrue(primary.contains("\"ItemAnimationId\": \"CastHurlCharging\""));
        assertTrue(primary.contains("\"ItemAnimationId\": \"CastHurlCharged\""));
        assertTrue(primary.contains("\"0.0\""));
        assertTrue(primary.contains("\"Command\": \"vampirismritual primaryrelease\""));
        assertTrue(primary.contains("\"Failed\""));
        assertFalse(primary.contains("vampirismritual secondary"));
        assertFalse(primary.contains("vampirismritual use"));
    }

    @Test
    void otherBindingsStayOnOriginalCommands() throws IOException {
        String ability1 = readResource("/Server/Item/Interactions/Vampirism/VampirismRitualTool_Ability1.json");
        String ability2 = readResource("/Server/Item/Interactions/Vampirism/VampirismRitualTool_Ability2.json");
        String secondary = readResource("/Server/Item/Interactions/Vampirism/VampirismRitualTool_Secondary.json");
        String ability3 = readResource("/Server/Item/Interactions/Vampirism/VampirismRitualTool_Ability3.json");
        String use = readResource("/Server/Item/Interactions/Vampirism/VampirismRitualTool_Use.json");
        String item = readResource("/Server/Item/Items/Vampirism/VampirismRitualTool.json");

        assertTrue(ability1.contains("\"Command\": \"vampirismritual ability1\""));
        assertTrue(ability1.contains("\"AllowIndefiniteHold\": true"));
        assertTrue(ability1.contains("\"0.0\""));
        assertTrue(ability1.contains("\"0.35\""));
        assertTrue(ability1.contains("\"WaitForAnimationToFinish\": true"));
        assertTrue(ability1.contains("\"ItemAnimationId\": \"CastPushCharged\""));
        assertTrue(ability2.contains("\"Command\": \"vampirismritual ability2\""));
        assertTrue(secondary.contains("\"Command\": \"vampirismritual secondary\""));
        assertTrue(secondary.contains("\"Type\": \"Simple\""));
        assertTrue(ability3.contains("\"Command\": \"vampirismritual ability3\""));
        assertTrue(ability3.contains("\"Type\": \"Simple\""));
        assertFalse(ability3.contains("\"AllowIndefiniteHold\": true"));
        assertTrue(use.contains("\"Command\": \"vampirismritual use\""));
        assertFalse(item.contains("\"Use\""));
        assertTrue(item.contains("Primary: Hold to trace a sigil, release to stop"));
        assertTrue(item.contains("Secondary: Cancel trace or abort ritual"));
        assertTrue(item.contains("Ability1: Open grimoire"));
        assertTrue(item.contains("Ability2: Reveal circle / pulse next sigil"));
        assertTrue(item.contains("Ability3: Commit completed circle / begin channel"));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = VampirismRitualToolBindingsTest.class.getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("Missing test resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
