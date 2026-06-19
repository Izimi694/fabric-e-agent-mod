package com.izimi.eagent.brainstem.action;

public record GoalAdoption(
    String attractorType,
    double boost,
    double commitScore,
    int ticksActive
) {
    public GoalAdoption {
        attractorType = attractorType == null ? "" : attractorType;
        boost = Math.max(0, Math.min(1, boost));
        commitScore = Math.max(0, Math.min(1, commitScore));
        ticksActive = Math.max(0, ticksActive);
    }

    public boolean isAdopted() { return commitScore >= 0.8; }
    public boolean isRejected() { return commitScore < 0.01; }

    public static GoalAdoption pending(String type, double boost) {
        double initial = boost * 0.5;
        return new GoalAdoption(type, boost, initial, 0);
    }

    public GoalAdoption withCommitIncrease() {
        double increased = commitScore + (1 - commitScore) * 0.02;
        return new GoalAdoption(attractorType, boost, Math.min(1, increased), ticksActive + 1);
    }

    public GoalAdoption withCommitDecay() {
        double decayed = commitScore * 0.9995;
        return new GoalAdoption(attractorType, boost, Math.max(0, decayed), ticksActive);
    }
}
