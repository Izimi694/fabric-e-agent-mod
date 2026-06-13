package com.izimi.eagent.cortex.api;

import com.izimi.eagent.hippocampus.MemoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.state.PlayerState;
import com.izimi.eagent.cortex.task.Task;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class AIMemoryGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final AIClient aiClient;

    public AIMemoryGenerator(AIClient aiClient) {
        this.aiClient = aiClient;
    }

    public MemoryEntry generateMemory(Task completedTask, PlayerState state) {
        if (!aiClient.isConfigured()) {
            return generateFallbackMemory(completedTask, state);
        }

        try {
            CompletableFuture<AIResponse> future = aiClient.generateMemory(completedTask, state);
            AIResponse response = future.get(15, java.util.concurrent.TimeUnit.SECONDS);

            if (response != null && response.getMemoryNote() != null
                    && !response.getMemoryNote().isEmpty()) {
                MemoryEntry memory = new MemoryEntry();
                memory.id = "mem_" + completedTask.getTaskId().replace("task_", "");
                memory.summary = response.getMemoryNote();
                memory.keyLearnings = new ArrayList<>();
                memory.relatedSkills = new ArrayList<>();
                memory.timestamp = System.currentTimeMillis();
                return memory;
            }

            return generateFallbackMemory(completedTask, state);
        } catch (Exception e) {
            LOGGER.error("[AIMemoryGenerator] AI记忆生成失败，使用回退: {}", e.getMessage());
            return generateFallbackMemory(completedTask, state);
        }
    }

    private MemoryEntry generateFallbackMemory(Task completedTask, PlayerState state) {
        MemoryEntry memory = new MemoryEntry();
        memory.id = "mem_" + completedTask.getTaskId().replace("task_", "");
        memory.summary = "完成任务: " + completedTask.getGoal();
        memory.keyLearnings = List.of("执行了操作: " + completedTask.getGoal());
        memory.relatedSkills = List.of("general");
        memory.timestamp = System.currentTimeMillis();
        return memory;
    }
}
