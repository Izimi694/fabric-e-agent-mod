package com.izimi.eagent.simulation;

import com.izimi.eagent.brainstem.adapter.TemporalScaler;
import com.izimi.eagent.brainstem.scheduler.ReflexSatisfaction;
import com.izimi.eagent.hormonal.HormonalSystem;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DecisionQualityTest {

    static final int RUNS = 100;
    static final double NOISE_STD = 0.01;
    static final Random RNG = new Random(42);

    @Test
    void benchmark() {
        List<BenchmarkReport> reports = new ArrayList<>();
        for (var sc : Scenarios.all()) {
            reports.add(runScenario(sc));
        }

        // ── summary report ──
        boolean win = true;
        System.out.println();
        System.out.println("=" .repeat(95));
        System.out.println("  三件套决策系统 — 模拟基准测试报告");
        System.out.println("=" .repeat(95));
        System.out.printf("  %-22s  %10s  %10s  %10s  %10s  %10s  %10s%n",
                "场景", "旧正确率", "新正确率", "旧耗时ns", "新耗时ns",
                "旧切换率", "新切换率");
        System.out.println("  " .repeat(93));

        int totalCorrectOld = 0, totalCorrectNew = 0, totalRuns = 0;
        for (var r : reports) {
            System.out.printf("  %-22s  %7.1f%%   %7.1f%%   %8.0f   %8.0f   %8.3f   %8.3f%n",
                    r.scenarioId() + "(" + r.totalRuns() + "x)",
                    r.correctnessOldPct(), r.correctnessNewPct(),
                    r.avgTimeOldNs(), r.avgTimeNewNs(),
                    r.switchRateOld(), r.switchRateNew());
            totalCorrectOld += r.correctOld();
            totalCorrectNew += r.correctNew();
            totalRuns += r.totalRuns();
            if (r.correctnessNewPct() < 90.0) {
                System.out.println("  ⚠  " + r.scenarioId() + " 新系统正确率 " +
                        String.format("%.1f", r.correctnessNewPct()) + "% < 90%");
                win = false;
            }
        }
        System.out.println("  " .repeat(93));
        System.out.printf("  总计      %5d/%-5d (%5.1f%%)   %5d/%-5d (%5.1f%%)%n",
                totalCorrectOld, totalRuns, totalCorrectOld * 100.0 / totalRuns,
                totalCorrectNew, totalRuns, totalCorrectNew * 100.0 / totalRuns);
        System.out.println("=" .repeat(95));
        System.out.println();

        // ── 写入报告文件 ──
        try {
            Path reportPath = Path.of("build", "simulation-report.txt");
            try (FileWriter fw = new FileWriter(reportPath.toFile())) {
                fw.write("=========================================================\n");
                fw.write("  三件套决策系统 — 模拟基准测试报告\n");
                fw.write("=========================================================\n");

                boolean allPass = true;
                for (var r : reports) {
                    String line = String.format("%s  OLD=%5.1f%%  NEW=%5.1f%%  time=%.0fns  switch OLD=%.3f NEW=%.3f\n",
                            r.scenarioId(), r.correctnessOldPct(), r.correctnessNewPct(),
                            r.avgTimeOldNs(),
                            r.switchRateOld(), r.switchRateNew());
                    fw.write(line);
                    if (r.correctnessNewPct() < 90.0) {
                        fw.write("  !! " + r.scenarioId() + " NEW correctness " +
                                String.format("%.1f", r.correctnessNewPct()) + "% < 90%\n");
                        allPass = false;
                    }
                }
                fw.write("=========================================================\n");
                String total = String.format("TOTAL  OLD=%5.1f%%  NEW=%5.1f%%  %s\n",
                        totalCorrectOld * 100.0 / totalRuns,
                        totalCorrectNew * 100.0 / totalRuns,
                        allPass ? "ALL PASS" : "SOME FAILED");
                fw.write(total);
                fw.write("=========================================================\n");
            }
            System.out.println("  >> 报告已写入: " + reportPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("  >> 写入报告失败: " + e.getMessage());
        }

        assertTrue(win, "部分场景新系统正确率 < 90%");
    }

    private BenchmarkReport runScenario(Scenario sc) {
        HormonalSystem h = new HormonalSystem();
        if (sc.hormonalPreset() != null) h.applyPreset(sc.hormonalPreset());
        double timeScale = TemporalScaler.computeTimeScale(h);

        System.out.println();
        System.out.println("── " + sc.id() + ": " + sc.description() + " ──");
        System.out.printf("  Domain=%s  timeScale=%.3f  expected=%s%n",
                sc.domain(), timeScale, sc.expectedBestId());

        List<ReflexCandidate> candidates = sc.candidates().stream()
                .filter(ReflexCandidate::isNearby)
                .collect(Collectors.toList());

        List<DecisionResult> results = new ArrayList<>();
        for (int i = 0; i < RUNS; i++) {
            long t0 = System.nanoTime();

            // ── 旧公式: product ──
            Map<String, Double> oldScores = new LinkedHashMap<>();
            String winnerOld = null;
            double bestOld = -1;
            for (var c : candidates) {
                double raw = OldScoringFormula.score(c);
                double noise = RNG.nextGaussian() * NOISE_STD * raw;
                double score = Math.max(0, raw + noise);
                oldScores.put(c.id(), score);
                if (score > bestOld) { bestOld = score; winnerOld = c.id(); }
            }

            // ── 新公式: ReflexSatisfaction ──
            Map<String, Double> newScores = new LinkedHashMap<>();
            String winnerNew = null;
            double bestNew = -1;
            for (var c : candidates) {
                double raw = ReflexSatisfaction.computeForDomainWithScale(
                        c.estimatedSeconds(), timeScale,
                        c.reflexWeight(), c.atomProficiency(),
                        c.bayesianPosterior(), c.decayFactor(),
                        c.riskScore(), c.resourceScore(),
                        sc.domain());
                double noise = RNG.nextGaussian() * NOISE_STD;
                double score = Math.max(0, raw + noise);
                newScores.put(c.id(), score);
                if (score > bestNew) { bestNew = score; winnerNew = c.id(); }
            }

            long elapsed = System.nanoTime() - t0;

            results.add(new DecisionResult(
                    sc.id(), winnerOld, winnerNew,
                    bestOld, bestNew,
                    oldScores, newScores, elapsed,
                    sc.isExpected(winnerOld), sc.isExpected(winnerNew)
            ));
        }

        // ── per-candidate stats ──
        for (var c : candidates) {
            double avgOld = results.stream()
                    .mapToDouble(r -> r.allScoresOld().get(c.id()))
                    .average().orElse(0);
            double avgNew = results.stream()
                    .mapToDouble(r -> r.allScoresNew().get(c.id()))
                    .average().orElse(0);
            long oldWins = results.stream()
                    .filter(r -> c.id().equals(r.winnerOld()))
                    .count();
            long newWins = results.stream()
                    .filter(r -> c.id().equals(r.winnerNew()))
                    .count();
            double product = OldScoringFormula.score(c);
            double newBase = newScoreBaseline(sc, timeScale, c, product);
            System.out.printf("  %-25s  old=%.4f(%3d%%)  new=%.4f(%3d%%)  atoms=%d  product=%.4f  base=%.4f%n",
                    c.id() + "(" + c.label() + ")",
                    avgOld, oldWins * 100 / results.size(),
                    avgNew, newWins * 100 / results.size(),
                    c.atomCount(), product, newBase);
        }

        long correctOld = results.stream().filter(DecisionResult::correctOld).count();
        long correctNew = results.stream().filter(DecisionResult::correctNew).count();
        System.out.printf("  >> 旧系统正确: %d/%d (%.1f%%)  新系统正确: %d/%d (%.1f%%)%n",
                correctOld, RUNS, correctOld * 100.0 / RUNS,
                correctNew, RUNS, correctNew * 100.0 / RUNS);

        double switchOld = results.stream()
                .filter(r -> !r.winnerOld().equals(results.getFirst().winnerOld()))
                .count();
        double switchNew = results.stream()
                .filter(r -> !r.winnerNew().equals(results.getFirst().winnerNew()))
                .count();
        System.out.printf("  >> 动作切换: 旧=%.0f 新=%.0f%n", switchOld, switchNew);

        return BenchmarkReport.aggregate(sc.id(), results);
    }

    /** 无噪声时的 base 分 (无领域时 = wTime*timeScore + wSuccess*product) */
    private static double newScoreBaseline(Scenario sc, double timeScale,
                                            ReflexCandidate c, double product) {
        return ReflexSatisfaction.computeForDomainWithScale(
                c.estimatedSeconds(), timeScale,
                c.reflexWeight(), c.atomProficiency(),
                c.bayesianPosterior(), c.decayFactor(),
                c.riskScore(), c.resourceScore(),
                sc.domain());
    }
}
