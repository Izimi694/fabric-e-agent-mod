package com.izimi.eagent.cortex.api;

import com.izimi.eagent.hippocampus.MemoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.state.PlayerState;
import com.izimi.eagent.cortex.task.Task;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class AITaskPlanner {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final AIClient aiClient;
    private final Deque<AIResponse> pendingResults = new ArrayDeque<>();
    private CompletableFuture<AIResponse> currentRequest = null;

    public AITaskPlanner(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    public void planTask(String playerMessage, PlayerState state, Task activeTask,
                         List<MemoryEntry> recentMemories, Map<String, Double> preferences) {
        if (!aiClient.isConfigured()) return;

        if (currentRequest != null && !currentRequest.isDone()) {
            return;
        }

        currentRequest = aiClient.planTask(playerMessage, state, activeTask,
                recentMemories, preferences);

        currentRequest.thenAccept(response -> {
            if (response != null && !response.isEmpty()) {
                synchronized (pendingResults) {
                    pendingResults.add(response);
                }
            }
        }).exceptionally(e -> {
            LOGGER.error("[AITaskPlanner] 规划失败: {}", e.getMessage());
            return null;
        });
    }

    public AIResponse pollResult() {
        synchronized (pendingResults) {
            return pendingResults.poll();
        }
    }

    public boolean hasPendingResult() {
        synchronized (pendingResults) {
            return !pendingResults.isEmpty();
        }
    }

    public boolean isBusy() {
        return currentRequest != null && !currentRequest.isDone();
    }
}
