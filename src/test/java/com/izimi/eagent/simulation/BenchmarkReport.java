package com.izimi.eagent.simulation;

import java.util.List;

public record BenchmarkReport(
    String scenarioId,
    int totalRuns,
    int correctOld,
    int correctNew,
    double avgTimeOldNs,
    double avgTimeNewNs,
    double avgSatisfactionOld,
    double avgSatisfactionNew,
    double switchRateOld,
    double switchRateNew
) {
    public double correctnessOldPct() { return totalRuns > 0 ? correctOld * 100.0 / totalRuns : 0; }
    public double correctnessNewPct() { return totalRuns > 0 ? correctNew * 100.0 / totalRuns : 0; }

    static BenchmarkReport aggregate(String id, List<DecisionResult> results) {
        int n = results.size();
        int co = 0, cn = 0;
        double to = 0, tn = 0;
        double so = 0, sn = 0;
        int swO = 0, swN = 0;
        String prevOld = null, prevNew = null;
        for (var r : results) {
            if (r.correctOld()) co++;
            if (r.correctNew()) cn++;
            to += r.elapsedNanos();
            tn += r.elapsedNanos(); // same timing for both
            so += r.scoreOld();
            sn += r.scoreNew();
            if (prevOld != null && !r.winnerOld().equals(prevOld)) swO++;
            if (prevNew != null && !r.winnerNew().equals(prevNew)) swN++;
            prevOld = r.winnerOld();
            prevNew = r.winnerNew();
        }
        return new BenchmarkReport(
            id, n, co, cn,
            to / n, tn / n,
            so / n, sn / n,
            (double) swO / (n - 1), (double) swN / (n - 1)
        );
    }
}
