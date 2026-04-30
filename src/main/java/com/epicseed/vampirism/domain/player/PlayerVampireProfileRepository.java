package com.epicseed.vampirism.domain.player;

import com.epicseed.epiccore.player.PlayerProfileRepository;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.hypixel.hytale.logger.HytaleLogger;

public final class PlayerVampireProfileRepository implements PlayerProfileRepository<PlayerVampireProfile> {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private final Path profilesDirectory;
    private final ObjectMapper mapper;

    public PlayerVampireProfileRepository(@Nonnull Path profilesDirectory) {
        this.profilesDirectory = profilesDirectory;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Nonnull
    public Path profilesDirectory() {
        return profilesDirectory;
    }

    @Nonnull
    public PlayerVampireProfile load(@Nonnull UUID uuid) {
        Path file = profileFile(uuid);
        if (!Files.exists(file)) {
            return new PlayerVampireProfile();
        }
        try {
            JsonNode root = mapper.readTree(file.toFile());
            PlayerVampireProfile profile = mapper.treeToValue(root, PlayerVampireProfile.class);
            applyLegacySatietyMigration(root, profile);
            profile.sanitize();
            return profile;
        } catch (IOException e) {
            LOGGER.atWarning().log("[PlayerVampireProfileRepository] Failed to load " + uuid + ": " + e.getMessage());
            return new PlayerVampireProfile();
        }
    }

    public void save(@Nonnull UUID uuid, @Nonnull PlayerVampireProfile profile) {
        profile.sanitize();
        try {
            Files.createDirectories(profilesDirectory);
            Path file = profileFile(uuid);
            Path tempFile = profilesDirectory.resolve(uuid + ".json.tmp");
            mapper.writeValue(tempFile.toFile(), profile);
            moveIntoPlace(tempFile, file);
        } catch (IOException e) {
            LOGGER.atSevere().log("[PlayerVampireProfileRepository] Failed to save " + uuid + ": " + e.getMessage());
        }
    }

    @Nonnull
    private Path profileFile(@Nonnull UUID uuid) {
        return profilesDirectory.resolve(uuid + ".json");
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

    private static void moveIntoPlace(@Nonnull Path tempFile, @Nonnull Path targetFile) throws IOException {
        try {
            Files.move(tempFile, targetFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
