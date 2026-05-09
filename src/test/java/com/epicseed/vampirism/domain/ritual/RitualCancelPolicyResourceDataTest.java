package com.epicseed.vampirism.domain.ritual;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RitualCancelPolicyResourceDataTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsExplicitCancelPolicyFromTemplateData() throws Exception {
        Path ritualDir = tempDir.resolve("data").resolve("vampirism").resolve("rituals");
        Files.createDirectories(ritualDir);
        Files.writeString(
                ritualDir.resolve("glyphs.json"),
                """
                        {
                          "glyphs": [
                            { "glyphId": "fang_wake", "symbolId": "fang_wake", "displayName": "Fang Wake", "traceSteps": [] }
                          ]
                        }
                        """,
                StandardCharsets.UTF_8);
        Files.writeString(
                ritualDir.resolve("templates.json"),
                """
                        {
                          "templates": [
                            {
                              "ritualId": "awakening",
                              "cancelPolicy": {
                                "timeoutSeconds": 45,
                                "maxDistanceFromAnchor": 8,
                                "distanceGraceSeconds": 1,
                                "cancelIfAnchorInvalid": true,
                                "cancelOnUnequipTool": true,
                                "cancelOnOwnerDeath": true
                              },
                              "points": [
                                { "id": "north", "glyphId": "fang_wake" }
                              ]
                            }
                          ]
                        }
                        """,
                StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{tempDir.toUri().toURL()}, null)) {
            VampiricRitualTemplateRegistry registry = new VampiricRitualTemplateRegistry(
                    "data/vampirism/rituals/templates.json",
                    "data/vampirism/rituals/glyphs.json",
                    classLoader);

            VampiricRitualCancelPolicy cancelPolicy = registry.template("awakening").orElseThrow().cancelPolicy();
            assertEquals(45d, cancelPolicy.timeoutSeconds());
            assertEquals(8d, cancelPolicy.maxDistanceFromAnchor());
            assertEquals(1d, cancelPolicy.distanceGraceSeconds());
            assertTrue(cancelPolicy.cancelIfAnchorInvalid());
            assertTrue(cancelPolicy.cancelOnUnequipTool());
            assertTrue(cancelPolicy.cancelOnOwnerDeath());
        }
    }

    @Test
    void skipsTemplatesWithMalformedCancelPolicyValues() throws Exception {
        Path ritualDir = tempDir.resolve("data").resolve("vampirism").resolve("rituals");
        Files.createDirectories(ritualDir);
        Files.writeString(
                ritualDir.resolve("glyphs.json"),
                """
                        {
                          "glyphs": [
                            { "glyphId": "fang_wake", "symbolId": "fang_wake", "displayName": "Fang Wake", "traceSteps": [] }
                          ]
                        }
                        """,
                StandardCharsets.UTF_8);
        Files.writeString(
                ritualDir.resolve("templates.json"),
                """
                        {
                          "templates": [
                            {
                              "ritualId": "negative_timeout",
                              "cancelPolicy": {
                                "timeoutSeconds": -1
                              },
                              "points": [
                                { "id": "north", "glyphId": "fang_wake" }
                              ]
                            },
                            {
                              "ritualId": "negative_distance",
                              "cancelPolicy": {
                                "maxDistanceFromAnchor": -1
                              },
                              "points": [
                                { "id": "north", "glyphId": "fang_wake" }
                              ]
                            },
                            {
                              "ritualId": "negative_grace",
                              "cancelPolicy": {
                                "distanceGraceSeconds": -1
                              },
                              "points": [
                                { "id": "north", "glyphId": "fang_wake" }
                              ]
                            }
                          ]
                        }
                        """,
                StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = new URLClassLoader(new java.net.URL[]{tempDir.toUri().toURL()}, null)) {
            VampiricRitualTemplateRegistry registry = new VampiricRitualTemplateRegistry(
                    "data/vampirism/rituals/templates.json",
                    "data/vampirism/rituals/glyphs.json",
                    classLoader);

            assertTrue(registry.template("negative_timeout").isEmpty());
            assertTrue(registry.template("negative_distance").isEmpty());
            assertTrue(registry.template("negative_grace").isEmpty());
            assertEquals(0, registry.templates().size());
        }
    }
}
