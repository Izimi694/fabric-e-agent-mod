package com.izimi.eagent.bayesian;

import java.util.List;

public class GatingArbiter {

    private static final double VARIANCE_SCALE = 0.25;
    private static final double CONTROLLABILITY_ENV_PENALTY = 0.5;

    private final BayesianModule bayesianModule;

    public GatingArbiter(BayesianModule bayesianModule) {
        this.bayesianModule = bayesianModule;
    }

    public double computeControllability(String reflexId, List<BayesianFeature> features) {
        if (reflexId == null) return 0.5;
        double confidence = bayesianModule.getConfidence(reflexId);
        double variance = confidence * (1.0 - confidence);
        if (variance <= 0) return 1.0;
        double controllability = 1.0 / (1.0 + variance / VARIANCE_SCALE);
        if (features != null) {
            for (BayesianFeature f : features) {
                if ("environment_change".equals(f.key()) && f.value()) {
                    controllability *= CONTROLLABILITY_ENV_PENALTY;
                }
            }
        }
        return Math.max(0, Math.min(1, controllability));
    }
}
