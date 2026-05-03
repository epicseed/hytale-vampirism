package com.epicseed.vampirism.domain.player;

import com.epicseed.epiccore.player.FileBackedPlayerProfileRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypixel.hytale.logger.HytaleLogger;

public final class PlayerVampireProfileRepository extends FileBackedPlayerProfileRepository<PlayerVampireProfile> {

    public PlayerVampireProfileRepository(@Nonnull Path profilesDirectory) {
        super(profilesDirectory, PlayerVampireProfile.class);
    }

    @Override
    protected PlayerVampireProfile createDefaultProfile() {
        return new PlayerVampireProfile();
    }

    @Override
    protected void sanitizeProfile(@Nonnull PlayerVampireProfile profile) {
        profile.sanitize();
    }

    @Override
    protected void migrateLoadedProfile(@Nonnull JsonNode root, @Nonnull PlayerVampireProfile profile) {
        applyLegacySatietyMigration(root, profile);
    }

    private static void applyLegacySatietyMigration(@Nonnull JsonNode root,
                                                    @Nonnull PlayerVampireProfile profile) {
        if (root.has("blood") || !root.has("satiety")) {
            return;
        }
        JsonNode satiety = root.get("satiety");
        if (!satiety.isNumber()) {
            return;
        }
        profile.blood = Math.max(0, Math.min(100, Math.round((float) satiety.asDouble() * 100f)));
    }

    @Override
    protected void onLoadFailure(@Nonnull UUID uuid, @Nonnull IOException e) {
        logFailure(false, "[PlayerVampireProfileRepository] Failed to load " + uuid + ": " + e.getMessage());
    }

    @Override
    protected void onSaveFailure(@Nonnull UUID uuid, @Nonnull IOException e) {
        logFailure(true, "[PlayerVampireProfileRepository] Failed to save " + uuid + ": " + e.getMessage());
    }

    private static void logFailure(boolean severe, String message) {
        try {
            HytaleLogger logger = HytaleLogger.forEnclosingClass();
            if (severe) {
                logger.atSevere().log(message);
            } else {
                logger.atWarning().log(message);
            }
            return;
        } catch (Throwable ignored) {
            // Fall back to JUL in tests and other non-Hytale environments.
        }

        Logger fallbackLogger = Logger.getLogger(PlayerVampireProfileRepository.class.getName());
        if (severe) {
            fallbackLogger.severe(message);
        } else {
            fallbackLogger.warning(message);
        }
    }
}
