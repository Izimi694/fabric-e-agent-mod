package com.izimi.eagent.cortex.task;

import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;

import java.nio.file.Path;
import java.util.*;

public class TaskLogger {

    public static final double EFFECTIVENESS_DECAY = 0.97;

    private final Path logDir;

    public TaskLogger(UUID botId) {
        this.logDir = FileUtil.getBotExecutionLogsDir(botId);
    }

    public TaskLogger(Path customLogDir) {
        this.logDir = customLogDir;
    }

    private void ensureDir() {
        if (!java.nio.file.Files.exists(logDir)) {
            try { java.nio.file.Files.createDirectories(logDir); } catch (Exception ignored) {}
        }
    }

    private Path logPath(String taskId) {
        ensureDir();
        return logDir.resolve("task_" + taskId + ".json");
    }

    public void logCreated(String taskId, String goal, String taskType) {
        Map<String, Object> data = loadOrCreate(taskId);
        data.put("taskId", taskId);
        data.put("goal", goal);
        data.put("taskType", taskType);
        data.put("status", "created");
        data.put("createdAt", System.currentTimeMillis());
        data.putIfAbsent("subtaskLogs", new ArrayList<>());
        save(taskId, data);
    }

    public void logSubTask(String taskId, String subTaskId, String action, String target,
                           boolean success, double effectiveness, int dagOrder) {
        logSubTask(taskId, subTaskId, action, target, success, effectiveness, dagOrder, null);
    }

    public void logSubTask(String taskId, String subTaskId, String action, String target,
                           boolean success, double effectiveness, int dagOrder, String failReason) {
        Map<String, Object> data = loadOrCreate(taskId);
        data.put("status", "running");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) data.computeIfAbsent("subtaskLogs", k -> new ArrayList<>());
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("subTaskId", subTaskId);
        entry.put("action", action);
        entry.put("target", target);
        entry.put("success", success);
        entry.put("effectiveness", effectiveness);
        entry.put("dagOrder", dagOrder);
        entry.put("timestamp", System.currentTimeMillis());
        if (failReason != null) entry.put("failReason", failReason);
        logs.add(entry);
        updateRollingEffectiveness(data, logs);
        save(taskId, data);
    }

    private void updateRollingEffectiveness(Map<String, Object> data, List<Map<String, Object>> logs) {
        double sum = 0.0;
        double weight = 1.0;
        double totalWeight = 0.0;
        for (int i = logs.size() - 1; i >= 0; i--) {
            Object eff = logs.get(i).get("effectiveness");
            if (eff instanceof Number) {
                sum += ((Number) eff).doubleValue() * weight;
                totalWeight += weight;
                weight *= EFFECTIVENESS_DECAY;
            }
        }
        data.put("rollingEffectiveness", totalWeight > 0 ? sum / totalWeight : 0.0);
    }

    public void logCompleted(String taskId, boolean success) {
        Map<String, Object> data = loadOrCreate(taskId);
        data.put("status", success ? "completed" : "failed");
        data.put("completedAt", System.currentTimeMillis());
        save(taskId, data);
    }

    public void logCancelled(String taskId, String reason) {
        Map<String, Object> data = loadOrCreate(taskId);
        data.put("status", "cancelled");
        data.put("cancelledAt", System.currentTimeMillis());
        data.put("cancelReason", reason);
        save(taskId, data);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getSubTaskLogs(String taskId) {
        Map<String, Object> data = loadOrCreate(taskId);
        return (List<Map<String, Object>>) data.getOrDefault("subtaskLogs", List.of());
    }

    public double getRollingEffectiveness(String taskId) {
        Map<String, Object> data = loadOrCreate(taskId);
        return ((Number) data.getOrDefault("rollingEffectiveness", 0.0)).doubleValue();
    }

    public String getStatus(String taskId) {
        Map<String, Object> data = loadOrCreate(taskId);
        return (String) data.getOrDefault("status", "unknown");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadOrCreate(String taskId) {
        Path path = logPath(taskId);
        Map<String, Object> data = JsonUtil.readMapFromFileSafe(path);
        return data != null ? data : new LinkedHashMap<>();
    }

    private void save(String taskId, Map<String, Object> data) {
        JsonUtil.writeToFileSafe(logPath(taskId), data);
    }
}
