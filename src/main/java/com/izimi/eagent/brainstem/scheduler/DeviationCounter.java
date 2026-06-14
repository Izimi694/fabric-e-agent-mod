package com.izimi.eagent.brainstem.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DeviationCounter {
    private static final double MAX_DRIFT = 8.0;
    private static final double DRIFT_PER_ACCEPT = 0.5;

    private final Map<String, Double> reflexDrift = new ConcurrentHashMap<>();
    private double totalDrift = 0.0;

    public void recordDrift(String reflexId, double contribution) {
        reflexDrift.merge(reflexId, contribution, Double::sum);
        totalDrift += contribution;
    }

    public void recordAcceptance() {
        recordDrift("_global_accept", DRIFT_PER_ACCEPT);
    }

    public boolean needsCalibration() {
        return totalDrift >= MAX_DRIFT;
    }

    public double getTotalDrift() {
        return totalDrift;
    }

    public double getReflexDrift(String reflexId) {
        return reflexDrift.getOrDefault(reflexId, 0.0);
    }

    public String getMostDriftedReflex() {
        return reflexDrift.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    public void reset() {
        reflexDrift.clear();
        totalDrift = 0.0;
    }
}
