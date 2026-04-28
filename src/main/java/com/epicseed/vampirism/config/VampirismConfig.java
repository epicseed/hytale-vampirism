package com.epicseed.vampirism.config;

import java.util.concurrent.CompletionException;

import javax.annotation.Nonnull;

import com.epicseed.vampirism.systems.VampireVitalitySystem;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.EnumCodec;
import com.hypixel.hytale.server.core.util.Config;

public class VampirismConfig {

    public static final BuilderCodec<VampirismConfig> CODEC =
        BuilderCodec.builder(VampirismConfig.class, VampirismConfig::new)
            // Satiety
            .append(new KeyedCodec<Double>("SatietyPerKill", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.satietyPerKill = v,
                    (cfg, i) -> cfg.satietyPerKill)
            .add()
            .append(new KeyedCodec<Double>("SatietyStarvingThreshold", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.satietyStarvingThreshold = v,
                    (cfg, i) -> cfg.satietyStarvingThreshold)
            .add()
            .append(new KeyedCodec<Double>("SatietyRecoveryThreshold", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.satietyRecoveryThreshold = v,
                    (cfg, i) -> cfg.satietyRecoveryThreshold)
            .add()
            .append(new KeyedCodec<Double>("StarvingDamageBonus", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.starvingDamageBonus = v,
                    (cfg, i) -> cfg.starvingDamageBonus)
            .add()
            .append(new KeyedCodec<Long>("SatietyUpdateIntervalMs", Codec.LONG),
                    (cfg, v, i) -> cfg.satietyUpdateIntervalMs = v,
                    (cfg, i) -> cfg.satietyUpdateIntervalMs)
            .add()
            .append(new KeyedCodec<Long>("HudInitDelayMs", Codec.LONG),
                    (cfg, v, i) -> cfg.hudInitDelayMs = v,
                    (cfg, i) -> cfg.hudInitDelayMs)
            .add()
            .append(new KeyedCodec<Long>("CooldownHudUpdateIntervalMs", Codec.LONG),
                    (cfg, v, i) -> cfg.cooldownHudUpdateIntervalMs = v,
                    (cfg, i) -> cfg.cooldownHudUpdateIntervalMs)
            .add()
            .append(new KeyedCodec<Integer>("VampireThroneRecoveryBlood", Codec.INTEGER),
                    (cfg, v, i) -> cfg.vampireThroneRecoveryBlood = v,
                    (cfg, i) -> cfg.vampireThroneRecoveryBlood)
            .add()
            // Damage
            .append(new KeyedCodec<Double>("SunlightDamageMultiplier", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.sunlightDamageMultiplier = v,
                    (cfg, i) -> cfg.sunlightDamageMultiplier)
            .add()
            .append(new KeyedCodec<Double>("BloodlustDamageMultiplier", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.bloodlustDamageMultiplier = v,
                    (cfg, i) -> cfg.bloodlustDamageMultiplier)
            .add()
            .append(new KeyedCodec<Double>("BloodlustLifesteal", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.bloodlustLifesteal = v,
                    (cfg, i) -> cfg.bloodlustLifesteal)
            .add()
            .append(new KeyedCodec<Double>("FallDamageReduction", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.fallDamageReduction = v,
                    (cfg, i) -> cfg.fallDamageReduction)
            .add()
            .append(new KeyedCodec<Double>("KillHealBonus", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.killHealBonus = v,
                    (cfg, i) -> cfg.killHealBonus)
            .add()
            // Speed
            .append(new KeyedCodec<Double>("SpeedNormal", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.speedNormal = v,
                    (cfg, i) -> cfg.speedNormal)
            .add()
            .append(new KeyedCodec<Double>("SpeedNight", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.speedNight = v,
                    (cfg, i) -> cfg.speedNight)
            .add()
            .append(new KeyedCodec<Double>("SpeedDay", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.speedDay = v,
                    (cfg, i) -> cfg.speedDay)
            .add()
            .append(new KeyedCodec<Integer>("SpeedTicksBetweenUpdates", Codec.INTEGER),
                    (cfg, v, i) -> cfg.speedTicksBetweenUpdates = v,
                    (cfg, i) -> cfg.speedTicksBetweenUpdates)
            .add()
            .append(new KeyedCodec<Integer>("EffectTicksBetweenUpdates", Codec.INTEGER),
                    (cfg, v, i) -> cfg.effectTicksBetweenUpdates = v,
                    (cfg, i) -> cfg.effectTicksBetweenUpdates)
            .add()
            // Sunburn (new)
            .append(new KeyedCodec<Double>("SunburnDamagePerSecond", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.sunburnDamagePerSecond = v,
                    (cfg, i) -> cfg.sunburnDamagePerSecond)
            .add()
            .append(new KeyedCodec<Long>("SunburnUpdateIntervalMs", Codec.LONG),
                    (cfg, v, i) -> cfg.sunburnUpdateIntervalMs = v,
                    (cfg, i) -> cfg.sunburnUpdateIntervalMs)
            .add()
            .append(new KeyedCodec<Integer>("SunburnTicksBetweenChecks", Codec.INTEGER),
                    (cfg, v, i) -> cfg.sunburnTicksBetweenChecks = v,
                    (cfg, i) -> cfg.sunburnTicksBetweenChecks)
            .add()
            .append(new KeyedCodec<Double>("SunburnTier2ThresholdSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.sunburnTier2ThresholdSeconds = v,
                    (cfg, i) -> cfg.sunburnTier2ThresholdSeconds)
            .add()
            .append(new KeyedCodec<Double>("SunburnTier3ThresholdSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.sunburnTier3ThresholdSeconds = v,
                    (cfg, i) -> cfg.sunburnTier3ThresholdSeconds)
            .add()
            // Infection (new)
            .append(new KeyedCodec<Boolean>("InfectionEnabled", Codec.BOOLEAN),
                    (cfg, v, i) -> cfg.infectionEnabled = v,
                    (cfg, i) -> cfg.infectionEnabled)
            .add()
            .append(new KeyedCodec<Double>("InfectionChance", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.infectionChance = v,
                    (cfg, i) -> cfg.infectionChance)
            .add()
            .append(new KeyedCodec<Double>("InfectionDurationSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.infectionDurationSeconds = v,
                    (cfg, i) -> cfg.infectionDurationSeconds)
            .add()
            // Cure (new)
            .append(new KeyedCodec<Double>("CureTimeSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.cureTimeSeconds = v,
                    (cfg, i) -> cfg.cureTimeSeconds)
            .add()
            // Time
            .append(new KeyedCodec<Integer>("DayStartHour", Codec.INTEGER),
                    (cfg, v, i) -> cfg.dayStartHour = v,
                    (cfg, i) -> cfg.dayStartHour)
            .add()
            .append(new KeyedCodec<Integer>("NightStartHour", Codec.INTEGER),
                    (cfg, v, i) -> cfg.nightStartHour = v,
                    (cfg, i) -> cfg.nightStartHour)
            .add()
            .append(new KeyedCodec<Integer>("ShelterDetectionHeight", Codec.INTEGER),
                    (cfg, v, i) -> cfg.shelterDetectionHeight = v,
                    (cfg, i) -> cfg.shelterDetectionHeight)
            .add()
            .append(new KeyedCodec<ShelterDetectionMode>("ShelterDetectionMode", new EnumCodec<>(ShelterDetectionMode.class)),
                    (cfg, v, i) -> cfg.shelterDetectionMode = v,
                    (cfg, i) -> cfg.shelterDetectionMode)
            .add()
            .append(new KeyedCodec<Integer>("SkyLightThreshold", Codec.INTEGER),
                    (cfg, v, i) -> cfg.skyLightThreshold = v,
                    (cfg, i) -> cfg.skyLightThreshold)
            .add()
            .append(new KeyedCodec<Integer>("SunRaycastMaxBlocks", Codec.INTEGER),
                    (cfg, v, i) -> cfg.sunRaycastMaxBlocks = v,
                    (cfg, i) -> cfg.sunRaycastMaxBlocks)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWaypointMinDistance", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWaypointMinDistance = v,
                    (cfg, i) -> cfg.nightHuntWaypointMinDistance)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWaypointMaxDistance", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWaypointMaxDistance = v,
                    (cfg, i) -> cfg.nightHuntWaypointMaxDistance)
            .add()
            .append(new KeyedCodec<Double>("NightHuntApproachMinDistance", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntApproachMinDistance = v,
                    (cfg, i) -> cfg.nightHuntApproachMinDistance)
            .add()
            .append(new KeyedCodec<Double>("NightHuntApproachMaxDistance", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntApproachMaxDistance = v,
                    (cfg, i) -> cfg.nightHuntApproachMaxDistance)
            .add()
            .append(new KeyedCodec<Double>("NightHuntApproachTimeoutSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntApproachTimeoutSeconds = v,
                    (cfg, i) -> cfg.nightHuntApproachTimeoutSeconds)
            .add()
            .append(new KeyedCodec<Integer>("NightHuntWaypointCount", Codec.INTEGER),
                    (cfg, v, i) -> cfg.nightHuntWaypointCount = v,
                    (cfg, i) -> cfg.nightHuntWaypointCount)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWaypointTimeoutSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWaypointTimeoutSeconds = v,
                    (cfg, i) -> cfg.nightHuntWaypointTimeoutSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntArrivalRadius", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntArrivalRadius = v,
                    (cfg, i) -> cfg.nightHuntArrivalRadius)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWaypointCancelDistance", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWaypointCancelDistance = v,
                    (cfg, i) -> cfg.nightHuntWaypointCancelDistance)
            .add()
            .append(new KeyedCodec<Double>("NightHuntSummonCancelRadius", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntSummonCancelRadius = v,
                    (cfg, i) -> cfg.nightHuntSummonCancelRadius)
            .add()
            .append(new KeyedCodec<Double>("NightHuntActivationHeightTolerance", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntActivationHeightTolerance = v,
                    (cfg, i) -> cfg.nightHuntActivationHeightTolerance)
            .add()
            .append(new KeyedCodec<Double>("NightHuntGuidePulseIntervalSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntGuidePulseIntervalSeconds = v,
                    (cfg, i) -> cfg.nightHuntGuidePulseIntervalSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntSummonDurationSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntSummonDurationSeconds = v,
                    (cfg, i) -> cfg.nightHuntSummonDurationSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntPreyLifetimeSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntPreyLifetimeSeconds = v,
                    (cfg, i) -> cfg.nightHuntPreyLifetimeSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntCooldownSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntCooldownSeconds = v,
                    (cfg, i) -> cfg.nightHuntCooldownSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntFailedCooldownSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntFailedCooldownSeconds = v,
                    (cfg, i) -> cfg.nightHuntFailedCooldownSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntIdleDelayMinSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntIdleDelayMinSeconds = v,
                    (cfg, i) -> cfg.nightHuntIdleDelayMinSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntIdleDelayMaxSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntIdleDelayMaxSeconds = v,
                    (cfg, i) -> cfg.nightHuntIdleDelayMaxSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWaypointRotationSpeedDegrees", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWaypointRotationSpeedDegrees = v,
                    (cfg, i) -> cfg.nightHuntWaypointRotationSpeedDegrees)
            .add()
            .append(new KeyedCodec<Double>("NightHuntGuideSpawnForwardOffset", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntGuideSpawnForwardOffset = v,
                    (cfg, i) -> cfg.nightHuntGuideSpawnForwardOffset)
            .add()
            .append(new KeyedCodec<Double>("NightHuntGuideSpawnYOffset", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntGuideSpawnYOffset = v,
                    (cfg, i) -> cfg.nightHuntGuideSpawnYOffset)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWispLiftDurationSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWispLiftDurationSeconds = v,
                    (cfg, i) -> cfg.nightHuntWispLiftDurationSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWaypointMarkerYOffset", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWaypointMarkerYOffset = v,
                    (cfg, i) -> cfg.nightHuntWaypointMarkerYOffset)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWispDestinationRadius", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWispDestinationRadius = v,
                    (cfg, i) -> cfg.nightHuntWispDestinationRadius)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWaypointUpdateIntervalSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWaypointUpdateIntervalSeconds = v,
                    (cfg, i) -> cfg.nightHuntWaypointUpdateIntervalSeconds)
            .add()
            .append(new KeyedCodec<Double>("NightHuntWispUpdateIntervalSeconds", Codec.DOUBLE),
                    (cfg, v, i) -> cfg.nightHuntWispUpdateIntervalSeconds = v,
                    (cfg, i) -> cfg.nightHuntWispUpdateIntervalSeconds)
            .add()
            .append(new KeyedCodec<Integer>("NightHuntMaxActiveWisps", Codec.INTEGER),
                    (cfg, v, i) -> cfg.nightHuntMaxActiveWisps = v,
                    (cfg, i) -> cfg.nightHuntMaxActiveWisps)
            .add()
            // Registry
            .append(new KeyedCodec<Boolean>("VampireDefaultEnabled", Codec.BOOLEAN),
                    (cfg, v, i) -> cfg.vampireDefaultEnabled = v,
                    (cfg, i) -> cfg.vampireDefaultEnabled)
            .add()
            .build();

    // Satiety
    private double satietyPerKill = 0.15;
    private double satietyStarvingThreshold = 0.2;
    private double satietyRecoveryThreshold = 0.3;
    private double starvingDamageBonus = 0.25;
    private long satietyUpdateIntervalMs = 2500L;
    private long hudInitDelayMs = 0L;
    private long cooldownHudUpdateIntervalMs = 0L;
    private int vampireThroneRecoveryBlood = 4;

    // Damage
    private double sunlightDamageMultiplier = 0.2;
    private double bloodlustDamageMultiplier = 1.5;
    private double bloodlustLifesteal = 0.15;
    private double fallDamageReduction = 1.0;
    private double killHealBonus = 0.1;

    // Speed
    private double speedNormal = 5.5;
    private double speedNight = 6.4;
    private double speedDay = 4.5;
    private int speedTicksBetweenUpdates = 10;

    // Effect modifiers
    private int effectTicksBetweenUpdates = 5;

    // Sunburn
    private double sunburnDamagePerSecond = 0.5;
    private long sunburnUpdateIntervalMs = 500L;
    private int sunburnTicksBetweenChecks = 5;
    private double sunburnTier2ThresholdSeconds = 15.0;
    private double sunburnTier3ThresholdSeconds = 30.0;

    // Infection
    private boolean infectionEnabled = true;
    private double infectionChance = 1.0;
    private double infectionDurationSeconds = 300.0;

    // Cure
    private double cureTimeSeconds = 300.0;

    // Time
    private int dayStartHour = 5;
    private int nightStartHour = 20;
    private int shelterDetectionHeight = 2;
    private ShelterDetectionMode shelterDetectionMode = ShelterDetectionMode.SUN_RAYCAST;
    private int skyLightThreshold = 14;
    private int sunRaycastMaxBlocks = 40;
    private double nightHuntWaypointMinDistance = 72.0;
    private double nightHuntWaypointMaxDistance = 112.0;
    private double nightHuntApproachMinDistance = 260.0;
    private double nightHuntApproachMaxDistance = 380.0;
    private double nightHuntApproachTimeoutSeconds = 240.0;
    private int nightHuntWaypointCount = 3;
    private double nightHuntWaypointTimeoutSeconds = 90.0;
    private double nightHuntArrivalRadius = 4.5;
    private double nightHuntWaypointCancelDistance = 160.0;
    private double nightHuntSummonCancelRadius = 8.0;
    private double nightHuntActivationHeightTolerance = 3.25;
    private double nightHuntGuidePulseIntervalSeconds = 5.0;
    private double nightHuntSummonDurationSeconds = 3.0;
    private double nightHuntPreyLifetimeSeconds = 90.0;
    private double nightHuntCooldownSeconds = 2400.0;
    private double nightHuntFailedCooldownSeconds = 1800.0;
    private double nightHuntIdleDelayMinSeconds = 18.0;
    private double nightHuntIdleDelayMaxSeconds = 32.0;
    private double nightHuntWaypointRotationSpeedDegrees = 150.0;
    private double nightHuntGuideSpawnForwardOffset = 1.35;
    private double nightHuntGuideSpawnYOffset = 1.35;
    private double nightHuntWispLiftDurationSeconds = 0.01;
    private double nightHuntWaypointMarkerYOffset = 1.1;
    private double nightHuntWispDestinationRadius = 1.4;
    private double nightHuntWaypointUpdateIntervalSeconds = 0.15;
    private double nightHuntWispUpdateIntervalSeconds = 0.15;
    private int nightHuntMaxActiveWisps = 4;

    // Registry defaults
    private boolean vampireDefaultEnabled = false;

    // Static accessor — initialized from the main plugin class
    private static Config<VampirismConfig> configHolder;

    public static void init(@Nonnull Config<VampirismConfig> config) {
        configHolder = config;
        loadAndSave();
    }

    public static void reload() {
        if (configHolder == null) {
            throw new IllegalStateException("VampirismConfig not initialized! Call init() first.");
        }
        loadAndSave();
    }

    @Nonnull
    public static VampirismConfig get() {
        if (configHolder == null) {
            throw new IllegalStateException("VampirismConfig not initialized! Call init() first.");
        }
        return configHolder.get();
    }

    private static void loadAndSave() {
        try {
            configHolder.load().join();
            configHolder.save().join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new IllegalStateException("Failed to load/save Vampirism config.", cause);
        }
    }

    public VampirismConfig() {}

    public int getSatietyPerKill() { return normalizeBloodConfigValue(satietyPerKill); }
    public int getSatietyStarvingThreshold() { return normalizeBloodConfigValue(satietyStarvingThreshold); }
    public int getSatietyRecoveryThreshold() { return normalizeBloodConfigValue(satietyRecoveryThreshold); }
    public float getStarvingDamageBonus() { return (float) Math.max(0d, starvingDamageBonus); }
    public long getSatietyUpdateIntervalMs() { return Math.max(100L, satietyUpdateIntervalMs); }
    public long getHudInitDelayMs() { return Math.max(0L, hudInitDelayMs); }
    public long getCooldownHudUpdateIntervalMs() { return Math.max(0L, cooldownHudUpdateIntervalMs); }
    public int getVampireThroneRecoveryBlood() {
        return Math.max(0, Math.min(VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS, vampireThroneRecoveryBlood));
    }

    public float getSunlightDamageMultiplier() { return (float) Math.max(0d, sunlightDamageMultiplier); }
    public float getBloodlustDamageMultiplier() { return (float) Math.max(0d, bloodlustDamageMultiplier); }
    public float getBloodlustLifesteal() { return (float) Math.max(0d, Math.min(1d, bloodlustLifesteal)); }
    public float getFallDamageReduction() { return (float) Math.max(0d, Math.min(1d, fallDamageReduction)); }
    public float getKillHealBonus() { return (float) Math.max(0d, killHealBonus); }

    public float getSpeedNormal() { return (float) Math.max(0d, speedNormal); }
    public float getSpeedNight() { return (float) Math.max(0d, speedNight); }
    public float getSpeedDay() { return (float) Math.max(0d, speedDay); }
    public int getSpeedTicksBetweenUpdates() { return Math.max(1, speedTicksBetweenUpdates); }
    public int getEffectTicksBetweenUpdates() { return Math.max(1, effectTicksBetweenUpdates); }

    public float getSunburnDamagePerSecond() { return (float) Math.max(0d, sunburnDamagePerSecond); }
    public long getSunburnUpdateIntervalMs() { return Math.max(100L, sunburnUpdateIntervalMs); }
    public int getSunburnTicksBetweenChecks() { return Math.max(1, sunburnTicksBetweenChecks); }
    public float getSunburnTier2ThresholdSeconds() { return (float) Math.max(1.0d, sunburnTier2ThresholdSeconds); }
    public float getSunburnTier3ThresholdSeconds() { return (float) Math.max(1.0d, sunburnTier3ThresholdSeconds); }

    public boolean isInfectionEnabled() { return infectionEnabled; }
    public float getInfectionChance() { return (float) Math.max(0d, Math.min(1d, infectionChance)); }
    public float getInfectionDurationSeconds() { return (float) Math.max(1d, infectionDurationSeconds); }

    public float getCureTimeSeconds() { return (float) Math.max(1d, cureTimeSeconds); }

    public int getDayStartHour() { return dayStartHour; }
    public int getNightStartHour() { return nightStartHour; }
    public int getShelterDetectionHeight() { return shelterDetectionHeight; }
    public ShelterDetectionMode getShelterDetectionMode() { return shelterDetectionMode; }
    public int getSkyLightThreshold() { return Math.max(0, Math.min(15, skyLightThreshold)); }
    public int getSunRaycastMaxBlocks() { return Math.max(1, Math.min(128, sunRaycastMaxBlocks)); }
    public float getNightHuntWaypointMinDistance() { return (float) Math.max(6.0d, nightHuntWaypointMinDistance); }
    public float getNightHuntWaypointMaxDistance() {
        return (float) Math.max(getNightHuntWaypointMinDistance() + 1.0d, nightHuntWaypointMaxDistance);
    }
    public float getNightHuntApproachMinDistance() {
        return (float) Math.max(getNightHuntWaypointMaxDistance() - 4.0d, nightHuntApproachMinDistance);
    }
    public float getNightHuntApproachMaxDistance() {
        return (float) Math.max(getNightHuntApproachMinDistance() + 1.0d, nightHuntApproachMaxDistance);
    }
    public float getNightHuntApproachTimeoutSeconds() {
        if (nightHuntApproachTimeoutSeconds <= 0d) {
            return 0f;
        }
        return (float) Math.max(1.0d, nightHuntApproachTimeoutSeconds);
    }
    public int getNightHuntWaypointCount() { return Math.max(1, nightHuntWaypointCount); }
    public float getNightHuntWaypointTimeoutSeconds() {
        if (nightHuntWaypointTimeoutSeconds <= 0d) {
            return 0f;
        }
        return (float) Math.max(1.0d, nightHuntWaypointTimeoutSeconds);
    }
    public float getNightHuntArrivalRadius() { return (float) Math.max(1.0d, nightHuntArrivalRadius); }
    public float getNightHuntWaypointCancelDistance() {
        if (nightHuntWaypointCancelDistance <= 0d) {
            return 0f;
        }
        return (float) Math.max(getNightHuntArrivalRadius(), nightHuntWaypointCancelDistance);
    }
    public float getNightHuntSummonCancelRadius() {
        return (float) Math.max(getNightHuntArrivalRadius(), nightHuntSummonCancelRadius);
    }
    public float getNightHuntActivationHeightTolerance() {
        return (float) Math.max(0.5d, nightHuntActivationHeightTolerance);
    }
    public float getNightHuntGuidePulseIntervalSeconds() { return (float) Math.max(0.1d, nightHuntGuidePulseIntervalSeconds); }
    public float getNightHuntSummonDurationSeconds() { return (float) Math.max(0.5d, nightHuntSummonDurationSeconds); }
    public float getNightHuntPreyLifetimeSeconds() { return (float) Math.max(5.0d, nightHuntPreyLifetimeSeconds); }
    public float getNightHuntCooldownSeconds() { return (float) Math.max(0.0d, nightHuntCooldownSeconds); }
    public float getNightHuntFailedCooldownSeconds() { return (float) Math.max(0.0d, nightHuntFailedCooldownSeconds); }
    public float getNightHuntIdleDelayMinSeconds() { return (float) Math.max(0.0d, nightHuntIdleDelayMinSeconds); }
    public float getNightHuntIdleDelayMaxSeconds() {
        return (float) Math.max(getNightHuntIdleDelayMinSeconds() + 1.0d, nightHuntIdleDelayMaxSeconds);
    }
    public float getNightHuntWaypointRotationSpeedDegrees() { return (float) Math.max(0.0d, nightHuntWaypointRotationSpeedDegrees); }
    public float getNightHuntGuideSpawnForwardOffset() { return (float) Math.max(0.1d, nightHuntGuideSpawnForwardOffset); }
    public float getNightHuntGuideSpawnYOffset() { return (float) Math.max(0.0d, nightHuntGuideSpawnYOffset); }
    public float getNightHuntWispLiftDurationSeconds() { return (float) Math.max(0.0d, nightHuntWispLiftDurationSeconds); }
    public float getNightHuntWaypointMarkerYOffset() { return (float) Math.max(0.0d, nightHuntWaypointMarkerYOffset); }
    public float getNightHuntWispDestinationRadius() { return (float) Math.max(0.25d, nightHuntWispDestinationRadius); }
    public float getNightHuntWaypointUpdateIntervalSeconds() { return (float) Math.max(0.05d, nightHuntWaypointUpdateIntervalSeconds); }
    public float getNightHuntWispUpdateIntervalSeconds() { return (float) Math.max(0.05d, nightHuntWispUpdateIntervalSeconds); }
    public int getNightHuntMaxActiveWisps() { return Math.max(1, nightHuntMaxActiveWisps); }

    public boolean isVampireDefaultEnabled() { return vampireDefaultEnabled; }

    private static int normalizeBloodConfigValue(double value) {
        double normalized = Math.max(0d, value);
        if (normalized <= 1d) {
            normalized *= VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS;
        }
        return Math.max(0, Math.min(VampireVitalitySystem.BASE_BLOOD_CAPACITY_UNITS, (int) Math.round(normalized)));
    }
}
