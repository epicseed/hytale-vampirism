package com.epicseed.vampirism.domain.blood;

public class BloodState {
    public volatile int blood = BloodService.BASE_BLOOD_CAPACITY_UNITS;
    public volatile int maxBlood = BloodService.BASE_BLOOD_CAPACITY_UNITS;
    public volatile boolean isStarving = false;
    public volatile long lastUpdateTime = System.currentTimeMillis();
    public volatile long lastCooldownHudUpdateTime = 0L;
    public volatile long firstSeenTime = System.currentTimeMillis();
    public volatile boolean hudInitFailed = false;
    public volatile boolean inputBindingsHidden = false;
    public volatile boolean relicInventoryFullNotified = false;
    public volatile float vampireThroneRecoveryAccumulator = 0f;
}
