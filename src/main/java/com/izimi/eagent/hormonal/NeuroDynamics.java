package com.izimi.eagent.hormonal;

public final class NeuroDynamics {

    private NeuroDynamics() {}

    public static double computeAttackInhibition(double serotonin, int failureCount, double confidence) {
        double inhibition = serotonin * 0.5;
        inhibition += Math.min(0.3, failureCount * 0.05);
        inhibition += (1.0 - confidence) * 0.2;
        return Math.min(0.9, inhibition);
    }

    public static double computeFlightExcitation(double dopamine, double ne, double novelty) {
        double excitation = dopamine * 0.3 + ne * 0.5;
        excitation += novelty * 0.2;
        return Math.min(0.9, excitation);
    }

    public static double computeAttackInhibition(NeuroState state, int failureCount, double confidence) {
        return computeAttackInhibition(state.serotonin(), failureCount, confidence);
    }

    public static double computeFlightExcitation(NeuroState state, double novelty) {
        return computeFlightExcitation(state.da(), state.ne(), novelty);
    }
}
