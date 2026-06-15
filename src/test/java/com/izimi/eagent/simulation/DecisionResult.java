package com.izimi.eagent.simulation;

import java.util.Map;

public record DecisionResult(
    String scenarioId,
    String winnerOld,
    String winnerNew,
    double scoreOld,
    double scoreNew,
    Map<String, Double> allScoresOld,
    Map<String, Double> allScoresNew,
    long elapsedNanos,
    boolean correctOld,
    boolean correctNew
) {}
