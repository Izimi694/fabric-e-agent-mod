package com.izimi.eagent.amygdala;

import com.izimi.eagent.util.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ReflectionEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");
    private static final int MAX_HISTORY = 50;
    private static final double PERSIST_INTERVAL = 5;

    private final Map<String, List<ReflectionRecord>> perReflexHistory = new ConcurrentHashMap<>();
    private final Map<String, PatternSummary> patternCache = new ConcurrentHashMap<>();
    private final UUID botId;
    private final Path storageDir;
    private int totalRecords = 0;

    public record ReflectionRecord(
            long timestamp,
            boolean success,
            String category,
            String action,
            String target,
            double posterior,
            boolean converged,
            String severity,
            double effectiveness
    ) {}

    public record PatternSummary(
            String reflexId,
            int totalAttempts,
            int successes,
            int failures,
            double successRate,
            String dominantFailureCategory,
            boolean deterministicSkip,
            int consecutiveFailures
    ) {
        public boolean shouldRetry() {
            return !deterministicSkip && successRate > 0.2;
        }

        public boolean shouldDegrade() {
            return consecutiveFailures >= 5 || (totalAttempts >= 10 && successRate < 0.3);
        }
    }

    public ReflectionEngine(UUID botId, Path storageDir) {
        this.botId = botId;
        this.storageDir = storageDir.resolve("reflections");
        loadAll();
    }

    public void record(boolean success, String reflexId, String category,
                       String action, String target, double posterior,
                       boolean converged, String severity, double effectiveness) {
        var record = new ReflectionRecord(
                System.currentTimeMillis(), success, category,
                action, target, posterior, converged, severity, effectiveness
        );
        perReflexHistory.computeIfAbsent(reflexId, k -> Collections.synchronizedList(new ArrayList<>())).add(record);
        totalRecords++;
        patternCache.remove(reflexId);

        if (totalRecords % PERSIST_INTERVAL == 0) {
            persist(reflexId);
        }
    }

    public PatternSummary getPattern(String reflexId) {
        PatternSummary cached = patternCache.get(reflexId);
        if (cached != null) return cached;

        List<ReflectionRecord> history = perReflexHistory.get(reflexId);
        if (history == null || history.isEmpty()) {
            var empty = new PatternSummary(reflexId, 0, 0, 0, 0.5, null, false, 0);
            patternCache.put(reflexId, empty);
            return empty;
        }

        int failures = 0;
        int successes = 0;
        int consecutiveFails = 0;
        Map<String, Integer> failureCats = new HashMap<>();
        boolean hasImpossible = false;
        int maxConsec = 0;

        for (int i = history.size() - 1; i >= 0; i--) {
            var r = history.get(i);
            if (r.success()) {
                successes++;
                consecutiveFails = 0;
            } else {
                failures++;
                consecutiveFails++;
                if (consecutiveFails > maxConsec) maxConsec = consecutiveFails;
                if ("IMPOSSIBLE_ATOM".equals(r.severity())) hasImpossible = true;
                if (r.category() != null) {
                    failureCats.merge(r.category(), 1, Integer::sum);
                }
            }
        }

        int total = successes + failures;
        double rate = total > 0 ? (double) successes / total : 0.5;
        String dominantCat = failureCats.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);

        var summary = new PatternSummary(reflexId, total, successes, failures,
                rate, dominantCat, hasImpossible, maxConsec);
        patternCache.put(reflexId, summary);
        return summary;
    }

    public List<String> getDegradedReflexes() {
        List<String> degraded = new ArrayList<>();
        for (String id : perReflexHistory.keySet()) {
            PatternSummary p = getPattern(id);
            if (p.shouldDegrade()) degraded.add(id);
        }
        return degraded;
    }

    public void adjustWeights(Map<String, Object> reflexData, String reflexId) {
        PatternSummary p = getPattern(reflexId);
        if (!p.shouldDegrade()) return;

        double currentStw = ((Number) reflexData.getOrDefault("shortTermWeight", 0.5)).doubleValue();
        double penalty = Math.min(0.3, p.consecutiveFailures() * 0.05);
        double newStw = Math.max(0, currentStw - penalty);
        reflexData.put("shortTermWeight", newStw);
        reflexData.put("degradedByReflection", true);
        reflexData.put("reflectionNote", "连续失败" + p.consecutiveFailures() + "次, 成功率" +
                String.format("%.0f", p.successRate() * 100) + "%, 降权" + String.format("%.2f", penalty));
    }

    private void persist(String reflexId) {
        Path file = storageDir.resolve(reflexId + ".json");
        List<ReflectionRecord> history = perReflexHistory.get(reflexId);
        if (history == null) return;
        try {
            java.nio.file.Files.createDirectories(storageDir);
            List<Map<String, Object>> serialized = new ArrayList<>();
            for (ReflectionRecord r : history) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("timestamp", r.timestamp());
                m.put("success", r.success());
                m.put("category", r.category());
                m.put("action", r.action());
                m.put("target", r.target());
                m.put("posterior", r.posterior());
                m.put("converged", r.converged());
                m.put("severity", r.severity());
                m.put("effectiveness", r.effectiveness());
                serialized.add(m);
            }
            JsonUtil.writeToFileSafeAtomic(file, Map.of("records", serialized));
        } catch (Exception e) {
            LOGGER.debug("[ReflectionEngine] persist失败: {}", e.getMessage());
        }
    }

    private void loadAll() {
        try {
            if (!java.nio.file.Files.exists(storageDir)) return;
            try (var stream = java.nio.file.Files.list(storageDir)) {
                for (var path : stream.toList()) {
                    if (!path.toString().endsWith(".json")) continue;
                    Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
                    if (data == null) continue;
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> records = (List<Map<String, Object>>) data.get("records");
                    if (records == null) continue;
                    String reflexId = path.getFileName().toString().replace(".json", "");
                    List<ReflectionRecord> list = Collections.synchronizedList(new ArrayList<>());
                    for (Map<String, Object> m : records) {
                        list.add(new ReflectionRecord(
                                ((Number) m.getOrDefault("timestamp", 0L)).longValue(),
                                (boolean) m.getOrDefault("success", false),
                                (String) m.get("category"),
                                (String) m.get("action"),
                                (String) m.get("target"),
                                ((Number) m.getOrDefault("posterior", 0.5)).doubleValue(),
                                (boolean) m.getOrDefault("converged", false),
                                (String) m.get("severity"),
                                ((Number) m.getOrDefault("effectiveness", 0.0)).doubleValue()
                        ));
                    }
                    perReflexHistory.put(reflexId, list);
                    totalRecords += list.size();
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[ReflectionEngine] loadAll: {}", e.getMessage());
        }
    }
}
