package com.epicseed.vampirism.registry;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class NightHuntSpawnRegistry {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String RESOURCE_PATH = "Common/UI/Custom/Vampirism/Data/NightHuntSpawns.json";

    private static NightHuntSpawnRegistry instance;

    private volatile RegistryData data;

    private NightHuntSpawnRegistry() {
        load();
    }

    public static void init() {
        instance = new NightHuntSpawnRegistry();
    }

    @Nonnull
    public static NightHuntSpawnRegistry get() {
        if (instance == null) {
            throw new IllegalStateException("NightHuntSpawnRegistry not initialized!");
        }
        return instance;
    }

    public void reload() {
        load();
    }

    @Nonnull
    public List<SpawnOption> getEligibleSpawns(@Nonnull SpawnContext context) {
        List<SpawnOption> eligible = new ArrayList<>();
        for (SpawnOptionData option : data.spawns) {
            if (!isValidSpawn(option) || !matchesSpawn(option, context)) {
                continue;
            }
            eligible.add(toOption(option));
        }
        return eligible;
    }

    @Nonnull
    public SpawnOption pickSpawn(@Nonnull SpawnContext context) {
        List<SpawnOptionData> eligible = collectEligibleSpawns(context);

        if (eligible.isEmpty()) {
            LOGGER.atWarning().log("[NightHuntSpawnRegistry] No eligible spawns for context, using fallback.");
            return defaultOption();
        }

        for (SpawnOptionData option : eligible) {
            if (ThreadLocalRandom.current().nextDouble() <= normalizeChance(option.chance)) {
                return toOption(option);
            }
        }

        return toOption(eligible.get(eligible.size() - 1));
    }

    @Nullable
    public List<RouteEventOption> getEligibleRouteEvents(@Nonnull RouteEventContext context) {
        List<RouteEventOption> eligible = new ArrayList<>();
        for (RouteEventData option : data.routeEvents) {
            if (!isValidRouteEvent(option) || !matchesRouteEvent(option, context)) {
                continue;
            }
            eligible.add(toOption(option));
        }
        return eligible;
    }

    @Nullable
    public RouteEventOption pickRouteEvent(@Nonnull RouteEventContext context) {
        List<RouteEventData> eligible = collectEligibleRouteEvents(context);

        for (RouteEventData option : eligible) {
            if (ThreadLocalRandom.current().nextDouble() <= normalizeChance(option.chance)) {
                return toOption(option);
            }
        }

        return null;
    }

    @Nullable
    public List<FailStateOption> getEligibleFailStates(@Nonnull FailStateContext context) {
        List<FailStateOption> eligible = new ArrayList<>();
        for (FailStateData option : data.failStates) {
            if (!isValidFailState(option) || !matchesFailState(option, context)) {
                continue;
            }
            eligible.add(toOption(option));
        }
        return eligible;
    }

    @Nullable
    public FailStateOption pickFailState(@Nonnull FailStateContext context) {
        List<FailStateData> eligible = collectEligibleFailStates(context);

        for (FailStateData option : eligible) {
            if (ThreadLocalRandom.current().nextDouble() <= normalizeChance(option.chance)) {
                return toOption(option);
            }
        }

        return null;
    }

    @Nonnull
    private List<SpawnOptionData> collectEligibleSpawns(@Nonnull SpawnContext context) {
        List<SpawnOptionData> eligible = new ArrayList<>();
        for (SpawnOptionData option : data.spawns) {
            if (!isValidSpawn(option) || !matchesSpawn(option, context)) {
                continue;
            }
            eligible.add(option);
        }
        return eligible;
    }

    @Nonnull
    private List<RouteEventData> collectEligibleRouteEvents(@Nonnull RouteEventContext context) {
        List<RouteEventData> eligible = new ArrayList<>();
        for (RouteEventData option : data.routeEvents) {
            if (!isValidRouteEvent(option) || !matchesRouteEvent(option, context)) {
                continue;
            }
            eligible.add(option);
        }
        return eligible;
    }

    @Nonnull
    private List<FailStateData> collectEligibleFailStates(@Nonnull FailStateContext context) {
        List<FailStateData> eligible = new ArrayList<>();
        for (FailStateData option : data.failStates) {
            if (!isValidFailState(option) || !matchesFailState(option, context)) {
                continue;
            }
            eligible.add(option);
        }
        return eligible;
    }

    @Nonnull
    private SpawnOption defaultOption() {
        return new SpawnOption(
                "Fox",
                "Marked Prey",
                1,
                "stalker",
                1,
                false,
                1.0f,
                null,
                0,
                2.5d,
                null,
                90.0f);
    }

    private void load() {
        try (InputStream in = getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.atWarning().log("[NightHuntSpawnRegistry] Missing resource " + RESOURCE_PATH + ", using fallback.");
                data = defaultData();
                return;
            }
            data = MAPPER.readValue(in, RegistryData.class);
            if (data == null || data.spawns == null || data.spawns.isEmpty()) {
                LOGGER.atWarning().log("[NightHuntSpawnRegistry] Empty resource " + RESOURCE_PATH + ", using fallback.");
                data = defaultData();
                return;
            }
            if (data.routeEvents == null) {
                data.routeEvents = new ArrayList<>();
            }
            if (data.failStates == null) {
                data.failStates = new ArrayList<>();
            }
        } catch (IOException e) {
            LOGGER.atSevere().log("[NightHuntSpawnRegistry] Failed to load resource " + RESOURCE_PATH + ": " + e.getMessage());
            data = defaultData();
        }
    }

    private static boolean isValidSpawn(@Nullable SpawnOptionData option) {
        return option != null && option.roleId != null && !option.roleId.isBlank();
    }

    private static boolean isValidRouteEvent(@Nullable RouteEventData option) {
        return option != null
                && ((option.text != null && !option.text.isBlank())
                || option.extraWaypoints != 0
                || option.visualTierDelta != 0
                || option.instantGuideBursts > 0);
    }

    private static boolean isValidFailState(@Nullable FailStateData option) {
        return option != null
                && ((option.text != null && !option.text.isBlank())
                || option.resumeGuiding
                || (option.ambushRoleId != null && !option.ambushRoleId.isBlank()));
    }

    private static boolean matchesSpawn(@Nonnull SpawnOptionData option, @Nonnull SpawnContext context) {
        return matchesCommonConditions(
                option.requiredAcquiredPoints,
                option.maxAcquiredPoints,
                option.minCompletedWaypoints,
                option.maxCompletedWaypoints,
                option.forcedOnly,
                option.naturalOnly,
                option.minHour,
                option.maxHour,
                option.minVisualTier,
                option.maxVisualTier,
                context.acquiredPoints(),
                context.completedWaypoints(),
                context.forced(),
                context.currentHour(),
                context.visualTier());
    }

    private static boolean matchesRouteEvent(@Nonnull RouteEventData option, @Nonnull RouteEventContext context) {
        return matchesCommonConditions(
                option.requiredAcquiredPoints,
                option.maxAcquiredPoints,
                option.minCompletedWaypoints,
                option.maxCompletedWaypoints,
                option.forcedOnly,
                option.naturalOnly,
                option.minHour,
                option.maxHour,
                option.minVisualTier,
                option.maxVisualTier,
                context.acquiredPoints(),
                context.completedWaypoints(),
                context.forced(),
                context.currentHour(),
                context.visualTier());
    }

    private static boolean matchesFailState(@Nonnull FailStateData option, @Nonnull FailStateContext context) {
        if (!matchesCommonConditions(
                option.requiredAcquiredPoints,
                option.maxAcquiredPoints,
                option.minCompletedWaypoints,
                option.maxCompletedWaypoints,
                option.forcedOnly,
                option.naturalOnly,
                option.minHour,
                option.maxHour,
                option.minVisualTier,
                option.maxVisualTier,
                context.acquiredPoints(),
                context.completedWaypoints(),
                context.forced(),
                context.currentHour(),
                context.visualTier())) {
            return false;
        }
        if (option.allowedFailurePhases == null || option.allowedFailurePhases.isEmpty()) {
            return true;
        }
        for (String phase : option.allowedFailurePhases) {
            if (phase != null && phase.equalsIgnoreCase(context.failurePhase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCommonConditions(int requiredAcquiredPoints,
                                                   int maxAcquiredPoints,
                                                   int minCompletedWaypoints,
                                                   int maxCompletedWaypoints,
                                                   boolean forcedOnly,
                                                   boolean naturalOnly,
                                                   int minHour,
                                                   int maxHour,
                                                   int minVisualTier,
                                                   int maxVisualTier,
                                                   int acquiredPoints,
                                                   int completedWaypoints,
                                                   boolean forced,
                                                   int currentHour,
                                                   int visualTier) {
        if (requiredAcquiredPoints > acquiredPoints) {
            return false;
        }
        if (maxAcquiredPoints >= 0 && acquiredPoints > maxAcquiredPoints) {
            return false;
        }
        if (completedWaypoints < Math.max(0, minCompletedWaypoints)) {
            return false;
        }
        if (maxCompletedWaypoints >= 0 && completedWaypoints > maxCompletedWaypoints) {
            return false;
        }
        if (forcedOnly && !forced) {
            return false;
        }
        if (naturalOnly && forced) {
            return false;
        }
        if (!matchesHour(currentHour, minHour, maxHour)) {
            return false;
        }
        if (visualTier < Math.max(1, minVisualTier)) {
            return false;
        }
        return maxVisualTier < 0 || visualTier <= maxVisualTier;
    }

    private static boolean matchesHour(int currentHour, int minHour, int maxHour) {
        if (minHour < 0 && maxHour < 0) {
            return true;
        }
        int normalizedHour = ((currentHour % 24) + 24) % 24;
        if (minHour < 0) {
            return normalizedHour <= maxHour;
        }
        if (maxHour < 0) {
            return normalizedHour >= minHour;
        }
        if (minHour <= maxHour) {
            return normalizedHour >= minHour && normalizedHour <= maxHour;
        }
        return normalizedHour >= minHour || normalizedHour <= maxHour;
    }

    private static double normalizeChance(double chance) {
        return Math.max(0.0d, Math.min(1.0d, chance));
    }

    @Nonnull
    private static SpawnOption toOption(@Nonnull SpawnOptionData option) {
        String displayName = option.displayName == null || option.displayName.isBlank()
                ? "Marked Prey"
                : option.displayName;
        return new SpawnOption(
                option.roleId,
                displayName,
                Math.max(1, option.dropPoints),
                normalizeString(option.archetype, "stalker"),
                clampTier(option.visualTier),
                option.elite,
                Math.max(0.1f, option.healthMultiplier),
                normalizeNullable(option.helperRoleId),
                Math.max(0, option.helperCount),
                Math.max(0.5d, option.helperSpreadRadius),
                normalizeNullable(option.onSpawnMessage),
                option.preyLifetimeSeconds > 0f ? option.preyLifetimeSeconds : 90.0f);
    }

    @Nonnull
    private static RouteEventOption toOption(@Nonnull RouteEventData option) {
        return new RouteEventOption(
                normalizeString(option.id, "route-event"),
                normalizeNullable(option.text),
                normalizeString(option.textColor, "dark_red"),
                option.extraWaypoints,
                option.visualTierDelta,
                Math.max(0, option.instantGuideBursts));
    }

    @Nonnull
    private static FailStateOption toOption(@Nonnull FailStateData option) {
        return new FailStateOption(
                normalizeString(option.id, "fail-state"),
                normalizeNullable(option.text),
                normalizeString(option.textColor, "dark_red"),
                option.cooldownSeconds,
                option.resumeGuiding,
                normalizeNullable(option.ambushRoleId),
                normalizeNullable(option.ambushDisplayName),
                Math.max(0, option.ambushDropPoints),
                option.ambushLifetimeSeconds > 0f ? option.ambushLifetimeSeconds : 60.0f,
                Math.max(0.1f, option.ambushHealthMultiplier),
                clampTier(option.ambushVisualTier),
                normalizeString(option.ambushArchetype, "ambusher"),
                normalizeNullable(option.ambushHelperRoleId),
                Math.max(0, option.ambushHelperCount),
                Math.max(0.5d, option.ambushHelperSpreadRadius));
    }

    private static int clampTier(int value) {
        return Math.max(1, Math.min(3, value));
    }

    @Nonnull
    private static String normalizeString(@Nullable String value, @Nonnull String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Nullable
    private static String normalizeNullable(@Nullable String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Nonnull
    private static RegistryData defaultData() {
        RegistryData defaults = new RegistryData();

        SpawnOptionData elder = new SpawnOptionData();
        elder.roleId = "Emberwulf";
        elder.displayName = "Ancient Bloodfang";
        elder.dropPoints = 3;
        elder.chance = 0.25d;
        elder.requiredAcquiredPoints = 10;
        elder.archetype = "pack";
        elder.visualTier = 3;
        elder.elite = true;
        elder.healthMultiplier = 1.85f;
        elder.helperRoleId = "Wolf_Black";
        elder.helperCount = 2;
        elder.helperSpreadRadius = 4.0d;
        elder.onSpawnMessage = "The trail ignites into an ancient alpha guarded by a bloodfang pack.";
        elder.preyLifetimeSeconds = 110.0f;
        defaults.spawns.add(elder);

        SpawnOptionData greater = new SpawnOptionData();
        greater.roleId = "Bear_Grizzly";
        greater.displayName = "Greater Marked Prey";
        greater.dropPoints = 2;
        greater.chance = 0.45d;
        greater.requiredAcquiredPoints = 7;
        greater.archetype = "brute";
        greater.visualTier = 2;
        greater.healthMultiplier = 1.22f;
        greater.onSpawnMessage = "The blood calls a heavier predator into the confrontation.";
        greater.preyLifetimeSeconds = 95.0f;
        defaults.spawns.add(greater);

        SpawnOptionData base = new SpawnOptionData();
        base.roleId = "Fox";
        base.displayName = "Marked Prey";
        base.dropPoints = 1;
        base.chance = 1.0d;
        base.requiredAcquiredPoints = 0;
        base.archetype = "stalker";
        base.visualTier = 1;
        base.healthMultiplier = 1.0f;
        base.preyLifetimeSeconds = 90.0f;
        defaults.spawns.add(base);

        RouteEventData surge = new RouteEventData();
        surge.id = "blood-surge";
        surge.text = "The trail thickens and pulses into a denser wine hue.";
        surge.chance = 0.35d;
        surge.requiredAcquiredPoints = 0;
        surge.minCompletedWaypoints = 1;
        surge.visualTierDelta = 1;
        surge.instantGuideBursts = 1;
        defaults.routeEvents.add(surge);

        RouteEventData split = new RouteEventData();
        split.id = "false-trail";
        split.text = "The blood trail splits and forces a wider turn before the prey.";
        split.chance = 0.20d;
        split.requiredAcquiredPoints = 3;
        split.minCompletedWaypoints = 1;
        split.extraWaypoints = 1;
        defaults.routeEvents.add(split);

        FailStateData stumble = new FailStateData();
        stumble.id = "trail-stumble";
        stumble.text = "The trail falters, but the scent of blood still lingers.";
        stumble.textColor = "yellow";
        stumble.chance = 0.55d;
        stumble.minCompletedWaypoints = 1;
        stumble.resumeGuiding = true;
        stumble.allowedFailurePhases.add("summoning");
        defaults.failStates.add(stumble);

        FailStateData ambush = new FailStateData();
        ambush.id = "blood-counter-ambush";
        ambush.text = "Your hesitation gave the hunt away. The prey counterattacks from the shadows.";
        ambush.chance = 0.35d;
        ambush.requiredAcquiredPoints = 4;
        ambush.minCompletedWaypoints = 2;
        ambush.allowedFailurePhases.add("summoning");
        ambush.allowedFailurePhases.add("prey-active");
        ambush.ambushRoleId = "Goblin_Thief";
        ambush.ambushDisplayName = "Blood Ambusher";
        ambush.ambushDropPoints = 1;
        ambush.ambushLifetimeSeconds = 55.0f;
        ambush.ambushHealthMultiplier = 1.25f;
        ambush.ambushVisualTier = 2;
        ambush.ambushArchetype = "ambusher";
        ambush.ambushHelperRoleId = "Goblin_Scrapper";
        ambush.ambushHelperCount = 1;
        ambush.ambushHelperSpreadRadius = 3.0d;
        defaults.failStates.add(ambush);

        return defaults;
    }

    @Nonnull
    private static ClassLoader getClassLoader() {
        ClassLoader loader = NightHuntSpawnRegistry.class.getClassLoader();
        return loader != null ? loader : ClassLoader.getSystemClassLoader();
    }

    public record SpawnContext(int acquiredPoints,
                               int completedWaypoints,
                               boolean forced,
                               int currentHour,
                               int visualTier) {
    }

    public record RouteEventContext(int acquiredPoints,
                                    int completedWaypoints,
                                    boolean forced,
                                    int currentHour,
                                    int visualTier) {
    }

    public record FailStateContext(int acquiredPoints,
                                   int completedWaypoints,
                                   boolean forced,
                                   int currentHour,
                                   int visualTier,
                                   @Nonnull String failurePhase) {
    }

    public record SpawnOption(@Nonnull String roleId,
                              @Nonnull String displayName,
                              int dropPoints,
                              @Nonnull String archetype,
                              int visualTier,
                              boolean elite,
                              float healthMultiplier,
                              @Nullable String helperRoleId,
                              int helperCount,
                              double helperSpreadRadius,
                              @Nullable String onSpawnMessage,
                              float preyLifetimeSeconds) {
    }

    public record RouteEventOption(@Nonnull String id,
                                   @Nullable String text,
                                   @Nonnull String textColor,
                                   int extraWaypoints,
                                   int visualTierDelta,
                                   int instantGuideBursts) {
    }

    public record FailStateOption(@Nonnull String id,
                                  @Nullable String text,
                                  @Nonnull String textColor,
                                  float cooldownSeconds,
                                  boolean resumeGuiding,
                                  @Nullable String ambushRoleId,
                                  @Nullable String ambushDisplayName,
                                  int ambushDropPoints,
                                  float ambushLifetimeSeconds,
                                  float ambushHealthMultiplier,
                                  int ambushVisualTier,
                                  @Nonnull String ambushArchetype,
                                  @Nullable String ambushHelperRoleId,
                                  int ambushHelperCount,
                                  double ambushHelperSpreadRadius) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RegistryData {
        public List<SpawnOptionData> spawns = new ArrayList<>();
        public List<RouteEventData> routeEvents = new ArrayList<>();
        public List<FailStateData> failStates = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConditionalEntryData {
        public double chance = 1.0d;
        public int requiredAcquiredPoints = 0;
        public int maxAcquiredPoints = -1;
        public int minCompletedWaypoints = 0;
        public int maxCompletedWaypoints = -1;
        public boolean forcedOnly = false;
        public boolean naturalOnly = false;
        public int minHour = -1;
        public int maxHour = -1;
        public int minVisualTier = 1;
        public int maxVisualTier = -1;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class SpawnOptionData extends ConditionalEntryData {
        public String roleId;
        public String displayName;
        public int dropPoints = 1;
        public String archetype = "stalker";
        public int visualTier = 1;
        public boolean elite = false;
        public float healthMultiplier = 1.0f;
        public String helperRoleId;
        public int helperCount = 0;
        public double helperSpreadRadius = 2.5d;
        public String onSpawnMessage;
        public float preyLifetimeSeconds = 90.0f;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class RouteEventData extends ConditionalEntryData {
        public String id;
        public String text;
        public String textColor = "dark_red";
        public int extraWaypoints = 0;
        public int visualTierDelta = 0;
        public int instantGuideBursts = 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class FailStateData extends ConditionalEntryData {
        public String id;
        public String text;
        public String textColor = "dark_red";
        public float cooldownSeconds = 45.0f;
        public boolean resumeGuiding = false;
        public List<String> allowedFailurePhases = new ArrayList<>();
        public String ambushRoleId;
        public String ambushDisplayName;
        public int ambushDropPoints = 0;
        public float ambushLifetimeSeconds = 60.0f;
        public float ambushHealthMultiplier = 1.0f;
        public int ambushVisualTier = 2;
        public String ambushArchetype = "ambusher";
        public String ambushHelperRoleId;
        public int ambushHelperCount = 0;
        public double ambushHelperSpreadRadius = 2.5d;
    }
}
