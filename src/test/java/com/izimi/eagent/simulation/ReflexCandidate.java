package com.izimi.eagent.simulation;

public record ReflexCandidate(
    String id,
    String label,
    int atomCount,
    double reflexWeight,
    double atomProficiency,
    double bayesianPosterior,
    double decayFactor,
    boolean isNearby,
    double riskScore,
    double resourceScore
) {
    public ReflexCandidate {
        riskScore = Double.isNaN(riskScore) ? 1.0 : Math.max(0, Math.min(1, riskScore));
        resourceScore = Double.isNaN(resourceScore) ? 1.0 : Math.max(0, Math.min(1, resourceScore));
    }

    public ReflexCandidate(String id, String label, int atomCount,
                            double reflexWeight, double atomProficiency,
                            double bayesianPosterior, double decayFactor,
                            boolean isNearby) {
        this(id, label, atomCount, reflexWeight, atomProficiency,
                bayesianPosterior, decayFactor, isNearby, 1.0, 1.0);
    }

    public double estimatedSeconds() {
        return Math.max(1, atomCount) * 2.0;
    }
}
