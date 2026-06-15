package com.izimi.eagent.simulation;

public final class OldScoringFormula {
    private OldScoringFormula() {}

    /** 旧系统评分: reflexWeight × atomProficiency × bayesianPosterior × decayFactor */
    public static double score(double reflexWeight, double atomProficiency,
                                double bayesianPosterior, double decayFactor) {
        return reflexWeight * atomProficiency * bayesianPosterior * decayFactor;
    }

    public static double score(ReflexCandidate c) {
        return score(c.reflexWeight(), c.atomProficiency(),
                c.bayesianPosterior(), c.decayFactor());
    }
}
