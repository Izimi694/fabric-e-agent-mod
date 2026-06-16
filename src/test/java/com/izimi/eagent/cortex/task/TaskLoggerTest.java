package com.izimi.eagent.cortex.task;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskLoggerTest {

    @TempDir
    Path tempDir;

    private TaskLogger logger;
    private String taskId;

    @BeforeEach
    void setUp() {
        logger = new TaskLogger(tempDir);
        taskId = "test_task_001";
    }

    @Test
    @DisplayName("logCreated writes initial task log")
    void logCreated() {
        logger.logCreated(taskId, "dig 10 stone", "sustained");
        assertEquals("created", logger.getStatus(taskId));
    }

    @Test
    @DisplayName("logSubTask adds entry and updates rolling effectiveness")
    void logSubTask() {
        logger.logCreated(taskId, "dig 10 stone", "sustained");
        logger.logSubTask(taskId, "dig_stone", "dig", "stone", true, 0.8, 0);
        logger.logSubTask(taskId, "craft_table", "craft", "table", true, 0.9, 1);

        List<Map<String, Object>> logs = logger.getSubTaskLogs(taskId);
        assertEquals(2, logs.size());
        assertEquals(0.8, (Double) logs.get(0).get("effectiveness"), 0.001);
        assertTrue(logger.getRollingEffectiveness(taskId) > 0);
    }

    @Test
    @DisplayName("logCompleted sets status to completed")
    void logCompleted() {
        logger.logCreated(taskId, "dig 10 stone", "sustained");
        logger.logCompleted(taskId, true);
        assertEquals("completed", logger.getStatus(taskId));
    }

    @Test
    @DisplayName("logCancelled sets status to cancelled")
    void logCancelled() {
        logger.logCreated(taskId, "dig 10 stone", "sustained");
        logger.logCancelled(taskId, "player_interrupt");
        assertEquals("cancelled", logger.getStatus(taskId));
    }

    @Test
    @DisplayName("getSubTaskLogs returns empty for unknown task")
    void getSubTaskLogsEmpty() {
        assertTrue(logger.getSubTaskLogs("nonexistent").isEmpty());
    }

    @Test
    @DisplayName("getRollingEffectiveness returns 0 for unknown task")
    void rollingEffectivenessDefault() {
        assertEquals(0.0, logger.getRollingEffectiveness("nonexistent"), 0.001);
    }

    @Test
    @DisplayName("getStatus returns unknown for unknown task")
    void statusUnknown() {
        assertEquals("unknown", logger.getStatus("nonexistent"));
    }

    @Test
    @DisplayName("rolling effectiveness decays with later entries")
    void rollingEffectivenessDecay() {
        logger.logCreated(taskId, "test", "sustained");
        logger.logSubTask(taskId, "s1", "a", "t", true, 1.0, 0);
        logger.logSubTask(taskId, "s2", "b", "t", true, 0.0, 1);
        double eff = logger.getRollingEffectiveness(taskId);
        assertTrue(eff > 0 && eff < 1.0, "rolling effectiveness should be between 0 and 1: " + eff);
    }

    @Test
    @DisplayName("multiple tasks are independent")
    void multipleTasksIndependent() {
        logger.logCreated("task_a", "goal a", "instant");
        logger.logCreated("task_b", "goal b", "sustained");
        assertEquals("created", logger.getStatus("task_a"));
        assertEquals("created", logger.getStatus("task_b"));
    }
}
