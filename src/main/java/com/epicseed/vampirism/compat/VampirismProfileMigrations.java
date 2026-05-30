package com.epicseed.vampirism.compat;

import com.epicseed.epiccore.compat.VersionedDocumentMigration;
import com.epicseed.epiccore.compat.VersionedMigrationRegistry;
import com.epicseed.epiccore.player.PlayerUxPreferenceKeys;
import com.epicseed.epiccore.vampirism.domain.player.PlayerVampireProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.Nonnull;

public final class VampirismProfileMigrations {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private VampirismProfileMigrations() {
    }

    @Nonnull
    public static VersionedMigrationRegistry registry() {
        return new VersionedMigrationRegistry()
                .currentVersion(PlayerVampireProfileRepository.DOCUMENT_KIND, CURRENT_SCHEMA_VERSION)
                .register(new LegacyProfileShapeMigration());
    }

    private static final class LegacyProfileShapeMigration implements VersionedDocumentMigration {

        @Nonnull
        @Override
        public String documentKind() {
            return PlayerVampireProfileRepository.DOCUMENT_KIND;
        }

        @Override
        public int fromVersion() {
            return 0;
        }

        @Override
        public int toVersion() {
            return CURRENT_SCHEMA_VERSION;
        }

        @Override
        public void migrate(@Nonnull ObjectNode document) {
            migrateLegacySatiety(document);
            migrateLegacyUxPreferences(document);
            migrateLegacyNightHuntCooldown(document);
        }

        @Nonnull
        @Override
        public String id() {
            return "vampirism-profile-legacy-shape";
        }

        private static void migrateLegacySatiety(ObjectNode document) {
            if (document.has("blood") || !document.has("satiety")) {
                return;
            }
            JsonNode satiety = document.get("satiety");
            if (!satiety.isNumber()) {
                return;
            }
            int blood = Math.max(0, Math.min(100, Math.round((float) satiety.asDouble() * 100f)));
            document.put("blood", blood);
        }

        private static void migrateLegacyUxPreferences(ObjectNode document) {
            ObjectNode uxPreferences = objectChild(document, "uxPreferences");
            migrateLegacyBoolean(document, "hideLevelProgressHud",
                    uxPreferences, "hiddenHuds", PlayerUxPreferenceKeys.LEVEL_PROGRESS_HUD);
            migrateLegacyBoolean(document, "hideXpProgressNotifications",
                    uxPreferences, "mutedNotifications", PlayerUxPreferenceKeys.XP_PROGRESS_NOTIFICATIONS);
            migrateLegacyBoolean(document, "hidePassiveNotifications",
                    uxPreferences, "mutedNotifications", PlayerUxPreferenceKeys.PASSIVE_EFFECT_NOTIFICATIONS);
        }

        private static void migrateLegacyBoolean(ObjectNode document,
                                                 String legacyField,
                                                 ObjectNode uxPreferences,
                                                 String targetArray,
                                                 String targetKey) {
            JsonNode value = document.get(legacyField);
            if (value == null || !value.isBoolean() || !value.asBoolean()) {
                return;
            }
            appendUnique(uxPreferences, targetArray, targetKey);
        }

        private static void migrateLegacyNightHuntCooldown(ObjectNode document) {
            long cooldownEndsAt = positiveLong(document.get("nightHuntCooldownEndsAtMs"));
            if (cooldownEndsAt <= 0L) {
                long cooldownMs = positiveLong(document.get("nightHuntCooldownMs"));
                if (cooldownMs > 0L) {
                    cooldownEndsAt = System.currentTimeMillis() + cooldownMs;
                    document.put("nightHuntCooldownEndsAtMs", cooldownEndsAt);
                }
            }
            if (cooldownEndsAt <= 0L) {
                return;
            }
            ObjectNode persistedState = objectChild(document, "persistedNightHuntState");
            if (positiveLong(persistedState.get("cooldownEndsAtMs")) <= 0L) {
                persistedState.put("cooldownEndsAtMs", cooldownEndsAt);
            }
        }

        private static ObjectNode objectChild(ObjectNode parent, String fieldName) {
            JsonNode existing = parent.get(fieldName);
            if (existing instanceof ObjectNode objectNode) {
                return objectNode;
            }
            ObjectNode objectNode = parent.objectNode();
            parent.set(fieldName, objectNode);
            return objectNode;
        }

        private static void appendUnique(ObjectNode parent, String arrayName, String value) {
            JsonNode existing = parent.get(arrayName);
            ArrayNode array = existing instanceof ArrayNode arrayNode ? arrayNode : parent.putArray(arrayName);
            for (JsonNode node : array) {
                if (node.isTextual() && value.equals(node.asText())) {
                    return;
                }
            }
            array.add(value);
        }

        private static long positiveLong(JsonNode node) {
            if (node == null || !node.canConvertToLong()) {
                return 0L;
            }
            return Math.max(0L, node.asLong());
        }
    }
}
