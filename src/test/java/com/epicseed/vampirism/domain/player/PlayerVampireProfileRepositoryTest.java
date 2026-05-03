package com.epicseed.vampirism.domain.player;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlayerVampireProfileRepositoryTest {

    private Path repositoryDir;

    @BeforeEach
    void setUp() {
        repositoryDir = Path.of("build/test-work/player-vampire-profile-repository", UUID.randomUUID().toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(repositoryDir);
    }

    @Test
    void loadKeepsLegacySatietyMigrationLocalToVampirism() throws IOException {
        UUID uuid = UUID.randomUUID();
        Files.createDirectories(repositoryDir);
        Files.writeString(repositoryDir.resolve(uuid + ".json"), """
                {
                  "points": 3,
                  "skills": ["mist-step"],
                  "satiety": 0.45
                }
                """);

        PlayerVampireProfile profile = new PlayerVampireProfileRepository(repositoryDir).load(uuid);

        assertEquals(3, profile.skillPoints);
        assertTrue(profile.unlockedSkills.contains("mist-step"));
        assertEquals(45, profile.blood);
    }

    @Test
    void savePreservesVampirismFieldNamesThroughSharedRepositoryBase() throws IOException {
        UUID uuid = UUID.randomUUID();
        PlayerVampireProfile profile = new PlayerVampireProfile();
        profile.skillPoints = 4;
        profile.blood = 80;
        profile.completedNightHunts = 2;

        PlayerVampireProfileRepository repository = new PlayerVampireProfileRepository(repositoryDir);
        repository.save(uuid, profile);

        String json = Files.readString(repositoryDir.resolve(uuid + ".json"));

        assertTrue(json.contains("\"points\""));
        assertTrue(json.contains("\"blood\""));
        assertTrue(json.contains("\"completedNightHunts\""));
        assertFalse(json.contains("\"skillPoints\""));
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(target -> {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }
}
