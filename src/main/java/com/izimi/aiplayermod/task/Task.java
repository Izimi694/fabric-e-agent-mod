package com.izimi.aiplayermod.task;

import java.util.HashMap;
import java.util.Map;

public class Task {
    public String taskId;
    public String type;
    public String goal;
    public String status;
    public Progress progress;
    public Context context;
    public String parentTaskId;
    public String preemptPolicy;

    public Task() {
        this.progress = new Progress();
        this.context = new Context();
        this.preemptPolicy = "ask";
    }

    public Task(String taskId, String type, String goal) {
        this();
        this.taskId = taskId;
        this.type = type;
        this.goal = goal;
        this.status = "running";
    }

    public static class Progress {
        public String currentStep;
        public int completedCount;
        public int targetCount;

        public Progress() {
            this.currentStep = "init";
            this.completedCount = 0;
            this.targetCount = 1;
        }
    }

    public static class Context {
        public int[] position;
        public Map<String, Integer> inventorySnapshot = new HashMap<>();
        public long startTime;

        public Context() {
            this.startTime = System.currentTimeMillis();
        }
    }

    public String getGoal() { return goal; }
    public String getStatus() { return status; }
    public String getTaskId() { return taskId; }

    public String getProgressSummary() {
        if (progress == null) return "0/0";
        return progress.completedCount + "/" + progress.targetCount + " (" + progress.currentStep + ")";
    }
}
