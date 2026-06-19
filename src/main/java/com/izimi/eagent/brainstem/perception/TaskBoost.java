package com.izimi.eagent.brainstem.perception;

public record TaskBoost(
    String targetType,
    double startBoost,
    double decayRate,
    int maxTicks
) {
    public static final double MIN_DECAY = 0.001;
    public static final double MAX_DECAY = 0.05;
    public static final int MIN_TICKS = 200;
    public static final int MAX_TICKS = 1200;
    public static final double DEFAULT_DECAY = 0.005;
    public static final int DEFAULT_TICKS = 600;

    public TaskBoost {
        decayRate = Math.max(MIN_DECAY, Math.min(MAX_DECAY, decayRate));
        maxTicks = Math.max(MIN_TICKS, Math.min(MAX_TICKS, maxTicks));
        startBoost = Math.max(0, Math.min(1, startBoost));
        targetType = targetType == null ? "" : targetType;
    }

    public double currentBoost(int elapsedTicks) {
        return startBoost * Math.exp(-decayRate * elapsedTicks);
    }

    public boolean isExpired(int elapsedTicks) {
        return elapsedTicks >= maxTicks || currentBoost(elapsedTicks) < 0.01;
    }

    public static TaskBoost of(String target, double boost) {
        return new TaskBoost(target, boost, DEFAULT_DECAY, DEFAULT_TICKS);
    }

    public static int computeHalfLifeTicks(double decayRate) {
        return (int) (Math.log(2) / Math.max(decayRate, 0.0001));
    }
}
