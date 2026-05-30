package com.epicseed.vampirism.commands;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

final class VampirismInteractionAssetMigrationTest {

    @Test
    void itemInteractionAssetsDoNotUseRemovedCommandCodec() throws IOException {
        Path interactionRoot = Path.of("src/main/resources/Server/Item/Interactions/Vampirism");
        try (Stream<Path> files = Files.walk(interactionRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(json.contains("\"Type\": \"Command\""), file.toString());
                assertFalse(json.contains("\"Command\""), file.toString());
            }
        }
    }

    @Test
    void commandBackedPotionEffectUsesCustomInteractionCodec() throws IOException {
        String potion = Files.readString(
                Path.of("src/main/resources/Server/Item/Items/Vampirism/Potion_Vampirism.json"),
                StandardCharsets.UTF_8);

        assertTrue(potion.contains("\"Type\": \"Vampirism_PotionTransform\""));
        assertFalse(potion.contains("\"Type\": \"Command\""));
        assertFalse(potion.contains("\"Command\""));
    }
}
