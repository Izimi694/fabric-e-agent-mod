package com.izimi.eagent.brainstem.perception;

import com.izimi.eagent.util.JsonUtil;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ValueRegistry {

    private static final double LEARN_INCREMENT = 0.3;
    private static final double LEARN_MAX = 1.0;
    private static final String FILE_NAME = "value_registry.json";

    private final Map<String, Double> learnedValues = new ConcurrentHashMap<>();
    private final Path storagePath;

    public ValueRegistry(Path storagePath) {
        this.storagePath = storagePath;
    }

    public double getValue(String blockId) {
        if (blockId == null) return 0;
        return learnedValues.getOrDefault(blockId.toLowerCase(), 0.0);
    }

    public boolean hasLearned(String blockId) {
        return blockId != null && learnedValues.containsKey(blockId.toLowerCase());
    }

    public void learnValue(String blockId, double value) {
        if (blockId == null) return;
        learnedValues.put(blockId.toLowerCase(), Math.max(0, Math.min(LEARN_MAX, value)));
        save();
    }

    public void learnBlockIfUnknown(String blockId, double satisfaction) {
        if (blockId == null) return;
        String key = blockId.toLowerCase();
        if (!learnedValues.containsKey(key) && satisfaction > 0.8) {
            double learned = Math.min(LEARN_MAX, SalienceMap.UNKNOWN_BLOCK_BASE_SALIENCE + LEARN_INCREMENT);
            learnedValues.put(key, learned);
            save();
        }
    }

    public void load() {
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(storagePath.resolve(FILE_NAME));
        if (data == null) return;
        for (var entry : data.entrySet()) {
            Object v = entry.getValue();
            if (v instanceof Number n) {
                learnedValues.put(entry.getKey(), clamp(n.doubleValue()));
            }
        }
    }

    public void save() {
        JsonUtil.writeToFileSafeAtomic(storagePath.resolve(FILE_NAME),
            new java.util.LinkedHashMap<>(learnedValues));
    }

    public int learnedCount() {
        return learnedValues.size();
    }

    public void clear() {
        learnedValues.clear();
    }

    private static double clamp(double v) {
        return Math.max(0, Math.min(LEARN_MAX, v));
    }
}
