package com.epicseed.epiccore.resource;

public final class ResourceStateMath {

    private ResourceStateMath() {
    }

    public static int sanitizeMax(int maxValue) {
        return Math.max(1, maxValue);
    }

    public static int clampCurrent(int currentValue, int maxValue) {
        int sanitizedMax = sanitizeMax(maxValue);
        return Math.max(0, Math.min(currentValue, sanitizedMax));
    }

    public static int spend(int currentValue, int amount) {
        if (amount <= 0) {
            return currentValue;
        }
        return Math.max(0, currentValue - amount);
    }

    public static int add(int currentValue, int maxValue, int amount) {
        if (amount <= 0) {
            return clampCurrent(currentValue, maxValue);
        }
        int sanitizedMax = sanitizeMax(maxValue);
        return Math.min(sanitizedMax, Math.max(0, currentValue) + amount);
    }
}
