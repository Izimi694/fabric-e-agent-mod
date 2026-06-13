package com.izimi.eagent.bayesian;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.izimi.eagent.hippocampus.MemoryEntry;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import com.izimi.eagent.util.api.FileSystem;

public class BayesianModule {

    // ── Layer 1: Global shared prior P(success) per reflexId ──
    private static final Map<String, Double> sharedPrior = new HashMap<>();
    private static Path sharedPriorPath;
    private static boolean sharedPriorLoaded = false;

    private static Path getSharedPriorPath() {
        if (sharedPriorPath == null) {
            sharedPriorPath = FileUtil.getBayesianDir().resolve("shared_prior.json");
        }
        return sharedPriorPath;
    }

    // ── Layer 2: Per-bot posterior P(featureValue | outcome) ──
    // featureKey → {"success": count, "failure": count}
    private final Map<String, Map<String, Integer>> featureGivenOutcome = new HashMap<>();

    // ── Layer 3: Anchoring context (current task filter) ──
    private String anchoringContext = "";

    // ── Per-bot posterior persistence path ──
    private Path posteriorPath;

    // ── Per-bot convergence tracking ──
    private final Map<String, PosteriorSnapshot> convergenceHistory = new HashMap<>();

    // ── Optional FileSystem injection for testability ──
    private final FileSystem fileSystem;
    private final UUID botId;

    // ── Constants ──
    private static final double PRIOR_LEARNING_RATE = 0.1;
    private static final double DEFAULT_PRIOR = 0.5;
    private static final double SMOOTHING = 0.5;
    private static final double CONVERGENCE_THRESHOLD = 1.0 / Math.E; // ≈ 0.3679

    public BayesianModule(UUID botId) {
        this(botId, null);
    }

    public BayesianModule(UUID botId, FileSystem fileSystem) {
        this.fileSystem = fileSystem;
        this.botId = botId;
        if (fileSystem != null) {
            this.posteriorPath = Path.of("memory", botId.toString(), "posterior.json");
        } else {
            loadSharedPrior();
            this.posteriorPath = FileUtil.getBotBayesianDir(botId).resolve("posterior.json");
            loadPosterior();
        }
    }

    // ── Shared prior (loaded once from disk) ──

    private static synchronized void loadSharedPrior() {
        if (sharedPriorLoaded) return;
        Path path = getSharedPriorPath();
        if (FileUtil.fileExists(path)) {
            Map<String, Object> raw = JsonUtil.readMapFromFileSafe(path);
            if (raw != null) {
                for (var entry : raw.entrySet()) {
                    sharedPrior.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                }
            }
        }
        sharedPriorLoaded = true;
    }

    public static synchronized void resetSharedState() {
        sharedPrior.clear();
        sharedPriorLoaded = false;
    }

    public static synchronized void saveSharedPrior() {
        JsonUtil.writeToFileSafeAtomic(getSharedPriorPath(), sharedPrior);
    }

    // ── Per-bot posterior persistence ──

    private void loadPosterior() {
        if (FileUtil.fileExists(posteriorPath)) {
            Map<String, Object> raw = JsonUtil.readMapFromFileSafe(posteriorPath);
            if (raw != null) {
                for (var entry : raw.entrySet()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> counts = (Map<String, Object>) entry.getValue();
                    Map<String, Integer> parsed = new HashMap<>();
                    for (var ce : counts.entrySet()) {
                        parsed.put(ce.getKey(), ((Number) ce.getValue()).intValue());
                    }
                    featureGivenOutcome.put(entry.getKey(), parsed);
                }
            }
        }
    }

    public void savePosterior() {
        if (fileSystem != null) {
            fileSystem.writeJson(posteriorPath, new HashMap<>(featureGivenOutcome));
        } else if (posteriorPath != null) {
            JsonUtil.writeToFileSafeAtomic(posteriorPath, new HashMap<>(featureGivenOutcome));
        }
    }

    // ── Public API: Prediction ──

    public double predictSuccess(String reflexId, List<BayesianFeature> features) {
        double prior = sharedPrior.getOrDefault(reflexId, DEFAULT_PRIOR);
        if (features == null || features.isEmpty()) return prior;

        double likelihood = 1.0;
        for (BayesianFeature f : features) {
            Map<String, Integer> counts = featureGivenOutcome.get(f.key());
            if (counts != null) {
                double pGivenSuccess = probability(counts, "success");
                likelihood *= f.value() ? pGivenSuccess : (1.0 - pGivenSuccess);
            }
        }

        double posterior = prior * likelihood;
        return Math.max(0, Math.min(1, posterior));
    }

    public BayesianPrediction predict(String reflexId, List<BayesianFeature> features) {
        double prior = sharedPrior.getOrDefault(reflexId, DEFAULT_PRIOR);
        if (features == null || features.isEmpty()) {
            return new BayesianPrediction(reflexId, prior, Collections.emptyList());
        }

        List<BayesianPrediction.FeatureContribution> contribs = new ArrayList<>();
        double likelihood = 1.0;

        for (BayesianFeature f : features) {
            Map<String, Integer> counts = featureGivenOutcome.get(f.key());
            if (counts != null) {
                double pGivenSuccess = probability(counts, "success");
                double pGivenFailure = probability(counts, "failure");
                double term = f.value() ? pGivenSuccess / Math.max(1e-10, pGivenFailure)
                                        : (1.0 - pGivenSuccess) / Math.max(1e-10, 1.0 - pGivenFailure);
                double impact = Math.log(term);
                contribs.add(new BayesianPrediction.FeatureContribution(f.key(), f.value(), impact));
                likelihood *= f.value() ? pGivenSuccess : (1.0 - pGivenSuccess);
            } else {
                contribs.add(new BayesianPrediction.FeatureContribution(f.key(), f.value(), 0.0));
            }
        }

        double posterior = prior * likelihood;
        return new BayesianPrediction(reflexId, Math.max(0, Math.min(1, posterior)), contribs);
    }

    // ── Public API: Update (error distillation core) ──

    public void update(String reflexId, List<BayesianFeature> features, boolean outcome) {
        updatePrior(reflexId, outcome);
        updatePosterior(features, outcome);
    }

    private void updatePrior(String reflexId, boolean outcome) {
        double current = sharedPrior.getOrDefault(reflexId, DEFAULT_PRIOR);
        double updated = current * (1.0 - PRIOR_LEARNING_RATE) + (outcome ? PRIOR_LEARNING_RATE : 0.0);
        updated = Math.max(0, Math.min(1, updated));
        sharedPrior.put(reflexId, updated);

        PosteriorSnapshot prev = convergenceHistory.get(reflexId);
        if (prev == null) {
            double deviation = Math.abs(updated - DEFAULT_PRIOR);
            double variance = Math.max(deviation, PRIOR_LEARNING_RATE);
            convergenceHistory.put(reflexId, new PosteriorSnapshot(reflexId, current, updated, variance, 1));
        } else {
            convergenceHistory.put(reflexId, new PosteriorSnapshot(
                    reflexId, prev.newProb(), updated,
                    prev.initialVariance(), prev.sampleCount() + 1));
        }
    }

    private void updatePosterior(List<BayesianFeature> features, boolean outcome) {
        if (features == null) return;
        String outcomeKey = outcome ? "success" : "failure";
        for (BayesianFeature f : features) {
            Map<String, Integer> counts = featureGivenOutcome
                    .computeIfAbsent(f.key(), k -> new HashMap<>());
            counts.merge(outcomeKey, 1, Integer::sum);
        }
    }

    // ── Public API: Memory relevance scoring (for Phase C) ──

    public double predictRelevance(String query, String memoryText) {
        if (query == null || memoryText == null) return 0.0;
        String q = query.toLowerCase();
        String m = memoryText.toLowerCase();

        long commonWords = Arrays.stream(q.split("\\s+"))
                .filter(w -> w.length() > 2 && m.contains(w))
                .count();
        long totalQueryWords = Arrays.stream(q.split("\\s+"))
                .filter(w -> w.length() > 2)
                .count();

        if (totalQueryWords == 0) return 0.0;
        double lexicalScore = (double) commonWords / totalQueryWords;

        double bayesianBonus = 0.0;
        for (String word : q.split("\\s+")) {
            if (word.length() > 2 && sharedPrior.containsKey(word)) {
                bayesianBonus += sharedPrior.get(word) - DEFAULT_PRIOR;
            }
        }

        return Math.max(0, Math.min(1, lexicalScore * 0.7 + bayesianBonus * 0.3));
    }

    // ── Public API: Memory-to-memory relevance (for MemoryGraph similarity) ──

    public double predictRelevance(MemoryEntry a, MemoryEntry b) {
        if (a == null || b == null) return 0.0;
        String query = a.summary;
        if (a.keyLearnings != null && !a.keyLearnings.isEmpty()) {
            query = (query != null ? query + " " : "") + String.join(" ", a.keyLearnings);
        }
        return predictRelevance(query, b.summary);
    }

    // ── Public API: Anchoring ──

    public void setAnchoringContext(String context) {
        this.anchoringContext = context != null ? context : "";
    }

    public String getAnchoringContext() {
        return anchoringContext;
    }

    public String getCurrentDirection() {
        if (sharedPrior.isEmpty()) return "暂无经验数据";

        List<Map.Entry<String, Double>> sorted = sharedPrior.entrySet().stream()
                .filter(e -> e.getValue() > DEFAULT_PRIOR)
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .toList();

        if (sorted.isEmpty()) return "暂无高置信度反射";

        String dir = sorted.stream()
                .map(e -> String.format("%s(%.2f)", e.getKey(), e.getValue()))
                .collect(Collectors.joining(", "));

        if (!anchoringContext.isEmpty()) {
            return "[" + anchoringContext + "] " + dir;
        }
        return dir;
    }

    public Map<String, Double> getTopPredictions(int topK) {
        return sharedPrior.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    // ── Stats ──

    public int getTrackedFeatureCount() {
        return featureGivenOutcome.size();
    }

    public int getTrackedReflexCount() {
        return sharedPrior.size();
    }

    public static synchronized void setPrior(String reflexId, double prior) {
        sharedPrior.put(reflexId, Math.max(0, Math.min(1, prior)));
    }

    public Map<String, Double> getSharedPrior() {
        return Collections.unmodifiableMap(sharedPrior);
    }

    // ── Public API: Convergence (e = 1/e threshold) ──

    public record PosteriorSnapshot(String reflexId, double oldProb, double newProb, double initialVariance, int sampleCount) {
        public double changeRate() {
            return initialVariance > 0 ? Math.abs(newProb - oldProb) / initialVariance : 1.0;
        }
        public boolean isConverged() {
            return sampleCount >= 5 && changeRate() < CONVERGENCE_THRESHOLD;
        }
    }

    public boolean isConverged(String reflexId) {
        PosteriorSnapshot snap = convergenceHistory.get(reflexId);
        return snap != null && snap.isConverged();
    }

    public PosteriorSnapshot getConvergence(String reflexId) {
        return convergenceHistory.get(reflexId);
    }

    public Map<String, PosteriorSnapshot> getAllConvergence() {
        return Collections.unmodifiableMap(convergenceHistory);
    }

    // ── Confidence ──

    public double getConfidence(String reflexId) {
        return sharedPrior.getOrDefault(reflexId, DEFAULT_PRIOR);
    }

    public boolean isPosteriorStable(String reflexId) {
        PosteriorSnapshot snap = convergenceHistory.get(reflexId);
        return snap != null && snap.sampleCount() >= 5 && snap.changeRate() < CONVERGENCE_THRESHOLD;
    }

    // ── Public API: Bidirectional Inference ──

    public List<Map.Entry<String, Double>> inferForward(BotState state, List<BayesianFeature> evidence) {
        if (state == null) return List.of();
        return sharedPrior.entrySet().stream()
                .filter(e -> state.isRelevantReflex(e.getKey()))
                .map(e -> {
                    double posterior = predictSuccess(e.getKey(), evidence);
                    return Map.entry(e.getKey(), posterior);
                })
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .toList();
    }

    public List<Map.Entry<String, Double>> inferBackward(String goalReflexId, List<BayesianFeature> evidence) {
        if (goalReflexId == null) return List.of();
        Map<String, Double> preconditionScores = new HashMap<>();
        double goalPrior = sharedPrior.getOrDefault(goalReflexId, DEFAULT_PRIOR);
        for (var entry : sharedPrior.entrySet()) {
            if (entry.getKey().equals(goalReflexId)) continue;
            double prior = entry.getValue();
            double score = prior * goalPrior;
            preconditionScores.put(entry.getKey(), score);
        }
        return preconditionScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(5)
                .toList();
    }

    public record BotState(String currentTask, String lastAction, String nearbyEntity, String nearbyBlock) {
        public boolean isRelevantReflex(String reflexId) {
            if (reflexId == null) return false;
            String lower = reflexId.toLowerCase();
            if (currentTask != null && lower.contains(currentTask.toLowerCase().replace(" ", "_"))) return true;
            if (nearbyEntity != null && lower.contains(nearbyEntity.toLowerCase())) return true;
            if (nearbyBlock != null && lower.contains(nearbyBlock.toLowerCase())) return true;
            return false;
        }
    }

    // ── Internal helpers ──

    private double probability(Map<String, Integer> counts, String key) {
        double total = (counts.getOrDefault("success", 0)
                + counts.getOrDefault("failure", 0) + SMOOTHING * 2);
        double count = counts.getOrDefault(key, 0) + SMOOTHING;
        return count / total;
    }
}
