package com.epicseed.vampirism.skill.registry;

import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks each player's skill points and unlocked skills.
 * Each player's data is stored in its own PlayerSkills/{uuid}.json file.
 * Data is loaded on player connect and saved+evicted on player disconnect.
 */
public class PlayerSkillRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static PlayerSkillRegistry instance;

    private final Path skillsDirectory;
    private final ConcurrentHashMap<UUID, PlayerSkillData> cache = new ConcurrentHashMap<>();
    /** UUIDs of players currently connected — used to evict offline players from cache after admin ops. */
    private final Set<UUID> onlinePlayers = ConcurrentHashMap.newKeySet();

    private PlayerSkillRegistry(@Nonnull Path dataDirectory) {
        this.skillsDirectory = dataDirectory.resolve("PlayerSkills");
    }

    public static void init(@Nonnull Path dataDirectory) {
        instance = new PlayerSkillRegistry(dataDirectory);
        LOGGER.atInfo().log("[PlayerSkillRegistry] Initialized. Per-player data directory: " + instance.skillsDirectory);
    }

    @Nonnull
    public static PlayerSkillRegistry get() {
        if (instance == null) throw new IllegalStateException("PlayerSkillRegistry not initialized!");
        return instance;
    }

    /** Call when a player connects — preloads their data from disk into cache. */
    public void onPlayerConnect(@Nonnull UUID uuid) {
        onlinePlayers.add(uuid);
        cache.computeIfAbsent(uuid, this::loadFromDisk);
        LOGGER.atInfo().log("[PlayerSkillRegistry] Loaded data for " + uuid);
    }

    /** Call when a player disconnects — persists their data and removes from cache. */
    public void onPlayerDisconnect(@Nonnull UUID uuid) {
        onlinePlayers.remove(uuid);
        PlayerSkillData d = cache.get(uuid);
        if (d != null) {
            // Save BEFORE removing from cache. A concurrent admin operation loading from disk
            // will then find up-to-date data rather than a stale snapshot.
            saveToDisk(uuid, d);
            cache.remove(uuid);
        }
    }

    public int getSkillPoints(@Nonnull UUID uuid) {
        return getOrLoad(uuid).skillPoints;
    }

    public int getAcquiredSkillPoints(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return Math.max(0, d.skillPoints + d.totalSpent);
        }
    }

    public void addSkillPoints(@Nonnull UUID uuid, int amount) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.skillPoints = Math.max(0, d.skillPoints + amount);
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
    }

    public void setSkillPoints(@Nonnull UUID uuid, int amount) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.skillPoints = Math.max(0, amount);
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
    }

    /**
     * Refunds all skill points spent at purchase time and clears all unlocked skills.
     * Uses the cost stored when each skill was purchased, so tree edits don't affect refunds.
     */
    public void resetSkills(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.skillPoints = Math.max(0, d.skillPoints + d.totalSpent);
            d.totalSpent = 0;
            d.unlockedSkills.clear();
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
    }

    public boolean hasSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        return getOrLoad(uuid).unlockedSkills.contains(skillId);
    }

    @Nonnull
    public Map<String, String> getRelicBindings(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return new LinkedHashMap<>(d.relicBindings);
        }
    }

    @Nullable
    public String getRelicBinding(@Nonnull UUID uuid, @Nonnull String slot) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return d.relicBindings.get(slot);
        }
    }

    public void setRelicBinding(@Nonnull UUID uuid, @Nonnull String slot, @Nonnull String abilityId) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.relicBindings.put(slot, abilityId);
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
    }

    public void clearRelicBinding(@Nonnull UUID uuid, @Nonnull String slot) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.relicBindings.remove(slot);
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
    }

    public void resetRelicBindings(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.relicBindings.clear();
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
    }

    public int getPersistedBlood(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return d.blood;
        }
    }

    public int getCompletedNightHunts(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return d.completedNightHunts;
        }
    }

    public void incrementCompletedNightHunts(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.completedNightHunts = Math.max(0, d.completedNightHunts + 1);
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
    }

    public void setPersistedBlood(@Nonnull UUID uuid, int blood) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.blood = Math.max(0, blood);
        }
    }

    @Nonnull
    public Map<String, Long> getPersistedAbilityCooldowns(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return new LinkedHashMap<>(d.abilityCooldowns);
        }
    }

    public void setPersistedAbilityCooldowns(@Nonnull UUID uuid, @Nonnull Map<String, Long> cooldowns) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.abilityCooldowns.clear();
            cooldowns.forEach((abilityId, remainingMs) -> {
                if (abilityId == null || abilityId.isBlank() || remainingMs == null || remainingMs <= 0L) {
                    return;
                }
                d.abilityCooldowns.put(abilityId, remainingMs);
            });
        }
    }

    public long getPersistedNightHuntCooldownMs(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return d.nightHuntCooldownMs;
        }
    }

    public void setPersistedNightHuntCooldownMs(@Nonnull UUID uuid, long cooldownMs) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.nightHuntCooldownMs = Math.max(0L, cooldownMs);
        }
    }

    public long getInfectionExpiresAtMs(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return Math.max(0L, d.infectionExpiresAtMs);
        }
    }

    public long getInfectionRemainingMs(@Nonnull UUID uuid) {
        return Math.max(0L, getInfectionExpiresAtMs(uuid) - System.currentTimeMillis());
    }

    public boolean isInfected(@Nonnull UUID uuid) {
        return getInfectionRemainingMs(uuid) > 0L;
    }

    public void setInfectionExpiresAtMs(@Nonnull UUID uuid, long expiresAtMs) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            d.infectionExpiresAtMs = Math.max(0L, expiresAtMs);
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
    }

    public void clearInfection(@Nonnull UUID uuid) {
        setInfectionExpiresAtMs(uuid, 0L);
    }

    /**
     * Atomically checks all preconditions and, if met, deducts the cost and unlocks the skill.
     * Returns true if successfully unlocked; false if already unlocked, insufficient points,
     * or a required skill is missing.
     */
    public boolean tryUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost,
                             @Nonnull Iterable<String> requirementIds) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            if (d.unlockedSkills.contains(skillId)) return false;
            if (d.skillPoints < cost) return false;
            for (String req : requirementIds) {
                if (!d.unlockedSkills.contains(req)) return false;
            }
            d.skillPoints -= cost;
            d.totalSpent += cost;
            d.unlockedSkills.add(skillId);
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
        return true;
    }

    public boolean grantSkill(@Nonnull UUID uuid, @Nonnull String skillId) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            if (d.unlockedSkills.contains(skillId)) return false;
            d.unlockedSkills.add(skillId);
        }
        saveToDisk(uuid, d);
        evictIfOffline(uuid);
        return true;
    }

    /** Non-mutating check — safe to call for UI display. */
    public boolean canUnlock(@Nonnull UUID uuid, @Nonnull String skillId, int cost,
                             @Nonnull Iterable<String> requirementIds) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            if (d.unlockedSkills.contains(skillId)) return false;
            if (d.skillPoints < cost) return false;
            for (String req : requirementIds) {
                if (!d.unlockedSkills.contains(req)) return false;
            }
        }
        return true;
    }

    @Nonnull
    public Set<String> getUnlockedSkills(@Nonnull UUID uuid) {
        PlayerSkillData d = getOrLoad(uuid);
        synchronized (d) {
            return new HashSet<>(d.unlockedSkills);
        }
    }

    // --- Persistence ---

    /** Evicts cache entry for offline players to prevent memory leaks from admin operations. */
    private void evictIfOffline(@Nonnull UUID uuid) {
        if (!onlinePlayers.contains(uuid)) {
            cache.remove(uuid);
        }
    }

    @Nonnull
    private PlayerSkillData getOrLoad(@Nonnull UUID uuid) {
        return cache.computeIfAbsent(uuid, this::loadFromDisk);
    }

    @Nonnull
    private PlayerSkillData loadFromDisk(@Nonnull UUID uuid) {
        Path file = skillsDirectory.resolve(uuid + ".json");
        if (!Files.exists(file)) return new PlayerSkillData();
        try {
            return parseJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            LOGGER.atWarning().log("[PlayerSkillRegistry] Failed to load " + uuid + ": " + e.getMessage());
            return new PlayerSkillData();
        }
    }

    private void saveToDisk(@Nonnull UUID uuid, @Nonnull PlayerSkillData d) {
        try {
            Files.createDirectories(skillsDirectory);
            Path file = skillsDirectory.resolve(uuid + ".json");
            String json;
            synchronized (d) {
                StringBuilder sb = new StringBuilder("{\n  \"points\": ")
                        .append(d.skillPoints)
                        .append(",\n  \"spent\": ")
                        .append(d.totalSpent)
                        .append(",\n  \"skills\": [");
                boolean first = true;
                for (String s : d.unlockedSkills) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append('"').append(s).append('"');
                }
                sb.append("],\n  \"relicBindings\": {");
                first = true;
                for (Map.Entry<String, String> entry : d.relicBindings.entrySet()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append("\n    \"")
                            .append(escape(entry.getKey()))
                            .append("\": \"")
                            .append(escape(entry.getValue()))
                            .append('"');
                }
                if (!d.relicBindings.isEmpty()) {
                    sb.append('\n').append("  ");
                }
                sb.append("},\n  \"blood\": ")
                        .append(d.blood)
                        .append(",\n  \"completedNightHunts\": ")
                        .append(d.completedNightHunts)
                        .append(",\n  \"nightHuntCooldownMs\": ")
                        .append(Math.max(0L, d.nightHuntCooldownMs))
                        .append(",\n  \"infectionExpiresAtMs\": ")
                        .append(Math.max(0L, d.infectionExpiresAtMs))
                        .append(",\n  \"abilityCooldowns\": {");
                first = true;
                for (Map.Entry<String, Long> entry : d.abilityCooldowns.entrySet()) {
                    if (!first) sb.append(", ");
                    first = false;
                    sb.append("\n    \"")
                            .append(escape(entry.getKey()))
                            .append("\": ")
                            .append(entry.getValue());
                }
                if (!d.abilityCooldowns.isEmpty()) {
                    sb.append('\n').append("  ");
                }
                sb.append("}\n}");
                json = sb.toString();
            }
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.atSevere().log("[PlayerSkillRegistry] Failed to save " + uuid + ": " + e.getMessage());
        }
    }

    @Nonnull
    private PlayerSkillData parseJson(@Nonnull String json) {
        PlayerSkillData d = new PlayerSkillData();
        Integer points = parseIntField(json, "points");
        if (points != null) {
            d.skillPoints = Math.max(0, points);
        }
        Integer spent = parseIntField(json, "spent");
        if (spent != null) {
            d.totalSpent = Math.max(0, spent);
        }
        int arrStart = json.indexOf('[');
        int arrEnd = json.indexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            for (String part : json.substring(arrStart + 1, arrEnd).split(",")) {
                String s = part.trim().replace("\"", "");
                if (!s.isEmpty()) d.unlockedSkills.add(s);
            }
        }

        int relicIdx = json.indexOf("\"relicBindings\"");
        if (relicIdx >= 0) {
            int objStart = json.indexOf('{', relicIdx);
            if (objStart >= 0) {
                int objEnd = findMatching(json, objStart);
                if (objEnd > objStart) {
                    parseStringMap(json.substring(objStart + 1, objEnd), d.relicBindings);
                }
            }
        }

        Integer blood = parseIntField(json, "blood");
        if (blood != null) {
            d.blood = Math.max(0, blood);
        }

        Integer completedNightHunts = parseIntField(json, "completedNightHunts");
        if (completedNightHunts != null) {
            d.completedNightHunts = Math.max(0, completedNightHunts);
        }

        Long nightHuntCooldownMs = parseLongField(json, "nightHuntCooldownMs");
        if (nightHuntCooldownMs != null) {
            d.nightHuntCooldownMs = Math.max(0L, nightHuntCooldownMs);
        }

        Long infectionExpiresAtMs = parseLongField(json, "infectionExpiresAtMs");
        if (infectionExpiresAtMs != null) {
            d.infectionExpiresAtMs = Math.max(0L, infectionExpiresAtMs);
        }
        if (d.infectionExpiresAtMs > 0L && d.infectionExpiresAtMs <= System.currentTimeMillis()) {
            d.infectionExpiresAtMs = 0L;
        }

        int satietyIdx = json.indexOf("\"satiety\"");
        if (satietyIdx >= 0 && d.blood == 100) {
            int colon = json.indexOf(':', satietyIdx + 8);
            if (colon >= 0) {
                StringBuilder sb = new StringBuilder();
                for (int i = colon + 1; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if ((c >= '0' && c <= '9') || c == '.' || c == '-') sb.append(c);
                    else if (sb.length() > 0) break;
                }
                try {
                    d.blood = Math.max(0, Math.min(100, Math.round(Float.parseFloat(sb.toString()) * 100f)));
                } catch (NumberFormatException ignored) {}
            }
        }

        int cooldownIdx = json.indexOf("\"abilityCooldowns\"");
        if (cooldownIdx >= 0) {
            int objStart = json.indexOf('{', cooldownIdx);
            if (objStart >= 0) {
                int objEnd = findMatching(json, objStart);
                if (objEnd > objStart) {
                    parseLongMap(json.substring(objStart + 1, objEnd), d.abilityCooldowns);
                }
            }
        }

        return d;
    }

    @Nullable
    private static Integer parseIntField(@Nonnull String json, @Nonnull String fieldName) {
        int fieldIdx = json.indexOf('"' + fieldName + '"');
        if (fieldIdx < 0) {
            return null;
        }
        int colon = json.indexOf(':', fieldIdx);
        if (colon < 0) {
            return null;
        }

        StringBuilder value = new StringBuilder();
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-') {
                value.append(c);
                continue;
            }
            if (value.length() > 0) {
                break;
            }
        }

        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    private static Long parseLongField(@Nonnull String json, @Nonnull String fieldName) {
        int fieldIdx = json.indexOf('"' + fieldName + '"');
        if (fieldIdx < 0) {
            return null;
        }
        int colon = json.indexOf(':', fieldIdx);
        if (colon < 0) {
            return null;
        }

        StringBuilder value = new StringBuilder();
        for (int i = colon + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if ((c >= '0' && c <= '9') || c == '-') {
                value.append(c);
                continue;
            }
            if (value.length() > 0) {
                break;
            }
        }

        if (value.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void parseStringMap(@Nonnull String s, @Nonnull Map<String, String> out) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            i = skipWs(s, i);
            if (i >= n || s.charAt(i) != '"') break;
            int kEnd = s.indexOf('"', i + 1);
            if (kEnd < 0) return;
            String k = s.substring(i + 1, kEnd);
            i = kEnd + 1;
            i = skipWs(s, i);
            if (i >= n || s.charAt(i) != ':') return;
            i++;
            i = skipWs(s, i);
            if (i >= n || s.charAt(i) != '"') return;
            int vEnd = s.indexOf('"', i + 1);
            if (vEnd < 0) return;
            out.put(k, s.substring(i + 1, vEnd));
            i = vEnd + 1;
            i = skipWs(s, i);
            if (i < n && s.charAt(i) == ',') i++;
        }
    }

    private static void parseLongMap(@Nonnull String s, @Nonnull Map<String, Long> out) {
        int i = 0;
        int n = s.length();
        while (i < n) {
            i = skipWs(s, i);
            if (i >= n || s.charAt(i) != '"') break;
            int kEnd = s.indexOf('"', i + 1);
            if (kEnd < 0) return;
            String key = s.substring(i + 1, kEnd);
            i = kEnd + 1;
            i = skipWs(s, i);
            if (i >= n || s.charAt(i) != ':') return;
            i++;
            i = skipWs(s, i);
            StringBuilder value = new StringBuilder();
            while (i < n) {
                char c = s.charAt(i);
                if (Character.isDigit(c)) {
                    value.append(c);
                    i++;
                    continue;
                }
                break;
            }
            if (!value.isEmpty()) {
                try {
                    long parsed = Long.parseLong(value.toString());
                    if (parsed > 0L) {
                        out.put(key, parsed);
                    }
                } catch (NumberFormatException ignored) {}
            }
            i = skipWs(s, i);
            if (i < n && s.charAt(i) == ',') i++;
        }
    }

    private static int skipWs(@Nonnull String s, int i) {
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                i++;
                continue;
            }
            break;
        }
        return i;
    }

    private static int findMatching(@Nonnull String s, int start) {
        int depth = 0;
        boolean inStr = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    @Nonnull
    private static String escape(@Nonnull String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class PlayerSkillData {
        volatile int skillPoints = 0;
        volatile int totalSpent = 0;
        volatile int blood = 100;
        volatile int completedNightHunts = 0;
        volatile long nightHuntCooldownMs = -1L;
        volatile long infectionExpiresAtMs = 0L;
        final Set<String> unlockedSkills = ConcurrentHashMap.newKeySet();
        final LinkedHashMap<String, String> relicBindings = new LinkedHashMap<>();
        final LinkedHashMap<String, Long> abilityCooldowns = new LinkedHashMap<>();
    }
}
