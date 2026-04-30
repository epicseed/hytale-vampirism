package com.epicseed.epiccore.resource;

public record ResourceGaugeValue(int currentValue, int maxValue) {

    public ResourceGaugeValue {
        maxValue = ResourceStateMath.sanitizeMax(maxValue);
        currentValue = ResourceStateMath.clampCurrent(currentValue, maxValue);
    }

    public double fillRatio() {
        return (double) currentValue / (double) maxValue;
    }

    public String displayText() {
        return currentValue + " / " + maxValue;
    }
}
