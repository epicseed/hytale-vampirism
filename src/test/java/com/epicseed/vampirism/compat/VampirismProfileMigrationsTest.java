package com.epicseed.vampirism.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.epicseed.epiccore.compat.SchemaVersionPolicy;
import com.epicseed.epiccore.player.PlayerUxPreferenceKeys;
import com.epicseed.epiccore.vampirism.domain.player.PlayerVampireProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.jupiter.api.Test;

class VampirismProfileMigrationsTest {

    @Test
    void migratesLegacySatietyUxAndNightHuntCooldown() throws Exception {
        ObjectNode document = (ObjectNode) new ObjectMapper().readTree("""
                {
                  "satiety": 0.42,
                  "hideLevelProgressHud": true,
                  "hideXpProgressNotifications": true,
                  "nightHuntCooldownMs": 120000
                }
                """);

        var result = VampirismProfileMigrations.registry().migrate(
                PlayerVampireProfileRepository.DOCUMENT_KIND,
                document,
                SchemaVersionPolicy.defaultPolicy());

        assertTrue(result.changed());
        assertEquals(VampirismProfileMigrations.CURRENT_SCHEMA_VERSION, document.get("_schemaVersion").asInt());
        assertEquals(42, document.get("blood").asInt());
        assertEquals(PlayerUxPreferenceKeys.LEVEL_PROGRESS_HUD,
                document.get("uxPreferences").get("hiddenHuds").get(0).asText());
        assertEquals(PlayerUxPreferenceKeys.XP_PROGRESS_NOTIFICATIONS,
                document.get("uxPreferences").get("mutedNotifications").get(0).asText());
        assertTrue(document.get("nightHuntCooldownEndsAtMs").asLong() > System.currentTimeMillis());
        assertEquals(document.get("nightHuntCooldownEndsAtMs").asLong(),
                document.get("persistedNightHuntState").get("cooldownEndsAtMs").asLong());
    }
}
