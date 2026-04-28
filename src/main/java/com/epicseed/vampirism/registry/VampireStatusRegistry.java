package com.epicseed.vampirism.registry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.config.VampirismConfig;
import com.epicseed.vampirism.skill.registry.PlayerSkillRegistry;
import com.hypixel.hytale.logger.HytaleLogger;

public class VampireStatusRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static VampireStatusRegistry instance;

    private final Path registryPath;

    /** In-memory set of UUIDs representing either vampires or exclusions (depending on defaultEnabled). */
    private final Set<UUID> entries = ConcurrentHashMap.newKeySet();
    /** Cached player names for display. */
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();

    private VampireStatusRegistry(@Nonnull Path registryPath) {
        this.registryPath = registryPath;
        load();
    }

    public static void init(@Nonnull Path dataDirectory) {
        instance = new VampireStatusRegistry(dataDirectory.resolve("Registry.json"));
        LOGGER.atInfo().log("[VampireStatusRegistry] Loaded " + instance.entries.size() + " entries. Default=" + VampirismConfig.get().isVampireDefaultEnabled());
    }

    @Nonnull
    public static VampireStatusRegistry get() {
        if (instance == null) {
            throw new IllegalStateException("VampireStatusRegistry not initialized! Call init() first.");
        }
        return instance;
    }

    private void load() {
        entries.clear();
        if (!Files.exists(registryPath)) return;
        try {
            String json = Files.readString(registryPath, StandardCharsets.UTF_8);
            // Simple extraction of quoted UUIDs from the JSON array
            int start = json.indexOf('[');
            int end = json.lastIndexOf(']');
            if (start < 0 || end < 0) return;
            String inner = json.substring(start + 1, end);
            for (String part : inner.split(",")) {
                String trimmed = part.trim().replace("\"", "");
                if (!trimmed.isEmpty()) {
                    try {
                        entries.add(UUID.fromString(trimmed));
                    } catch (IllegalArgumentException e) {
                        LOGGER.atWarning().log("[VampireStatusRegistry] Ignoring invalid UUID: " + trimmed);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.atSevere().log("[VampireStatusRegistry] Failed to load: " + e.getMessage());
        }
    }

    public boolean isVampire(@Nonnull UUID uuid) {
        return isPermanentVampire(uuid) || PlayerSkillRegistry.get().isInfected(uuid);
    }

    public boolean isPermanentVampire(@Nonnull UUID uuid) {
        boolean defaultEnabled = VampirismConfig.get().isVampireDefaultEnabled();
        return defaultEnabled ? !entries.contains(uuid) : entries.contains(uuid);
    }

    /** Returns true if the player was newly added as a vampire. */
    public boolean addVampire(@Nonnull UUID uuid, @Nonnull String name) {
        playerNames.put(uuid, name);
        boolean defaultEnabled = VampirismConfig.get().isVampireDefaultEnabled();
        boolean changed = defaultEnabled ? entries.remove(uuid) : entries.add(uuid);
        boolean infectionCleared = PlayerSkillRegistry.get().getInfectionExpiresAtMs(uuid) > 0L;
        if (infectionCleared) {
            PlayerSkillRegistry.get().clearInfection(uuid);
        }
        if (changed) {
            save();
            LOGGER.atInfo().log("[VampireStatusRegistry] " + name + " is now a vampire.");
        }
        return changed || infectionCleared;
    }

    /** Returns true if vampirism was removed. */
    public boolean removeVampire(@Nonnull UUID uuid, @Nonnull String name) {
        playerNames.put(uuid, name);
        boolean defaultEnabled = VampirismConfig.get().isVampireDefaultEnabled();
        boolean changed = defaultEnabled ? entries.add(uuid) : entries.remove(uuid);
        boolean infectionCleared = PlayerSkillRegistry.get().getInfectionExpiresAtMs(uuid) > 0L;
        if (infectionCleared) {
            PlayerSkillRegistry.get().clearInfection(uuid);
        }
        if (changed) {
            save();
            LOGGER.atInfo().log("[VampireStatusRegistry] " + name + " is no longer a vampire.");
        }
        return changed || infectionCleared;
    }

    /** Toggles vampirism and returns whether the player IS a vampire after the toggle. */
    public boolean toggleVampire(@Nonnull UUID uuid, @Nonnull String name) {
        if (isPermanentVampire(uuid)) {
            removeVampire(uuid, name);
            return false;
        } else {
            addVampire(uuid, name);
            return true;
        }
    }

    public void reload() {
        load();
        LOGGER.atInfo().log("[VampireStatusRegistry] Reloaded " + entries.size() + " entries.");
    }

    /** Returns the current tracked entries (interpretation depends on defaultEnabled). */
    @Nonnull
    public Map<UUID, String> getRegisteredEntries() {
        LinkedHashMap<UUID, String> result = new LinkedHashMap<>();
        for (UUID uuid : entries) {
            result.put(uuid, playerNames.getOrDefault(uuid, uuid.toString()));
        }
        return result;
    }

    public boolean isDefaultEnabled() {
        return VampirismConfig.get().isVampireDefaultEnabled();
    }

    private void save() {
        try {
            Files.createDirectories(registryPath.getParent());
            StringBuilder json = new StringBuilder("{\n  \"Entries\": [");
            List<UUID> list = new ArrayList<>(entries);
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) json.append(", ");
                json.append("\"").append(list.get(i)).append("\"");
            }
            json.append("]\n}");
            Files.writeString(registryPath, json.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.atSevere().log("[VampireStatusRegistry] Failed to save: " + e.getMessage());
        }
    }
}
