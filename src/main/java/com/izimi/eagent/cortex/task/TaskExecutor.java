package com.izimi.eagent.cortex.task;

import com.izimi.eagent.log.ExecutionLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.brainstem.skill.Skill;
import com.izimi.eagent.brainstem.skill.SkillManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class TaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final TaskManager taskManager;
    private final SkillManager skillManager;
    private final ExecutionLogger executionLogger;

    private int executionTick = 0;
    private static final int SKILL_TIMEOUT_TICKS = 6000;
    private static final double ACCEPTANCE_THRESHOLD = 0.6;
    private static final int MAX_UNABLE_RETRIES = 5;

    private Consumer<Double> onAcceptDrift;
    private Consumer<ServerPlayerEntity> onUnableExhausted;

    public TaskExecutor(TaskManager taskManager, SkillManager skillManager,
                         ExecutionLogger executionLogger) {
        this.taskManager = taskManager;
        this.skillManager = skillManager;
        this.executionLogger = executionLogger;
    }

    public void executeTask(ServerPlayerEntity bot, Task task) {
        if (bot == null || task == null) return;
        executionTick++;
        if (checkTimeout(task)) return;

        Task.SubTask current = getCurrentSubTask(task);
        if (current == null) {
            handleNoSubTask(task);
            return;
        }
        executeOneSubTask(bot, task, current);
    }

    private boolean checkTimeout(Task task) {
        if (executionTick <= SKILL_TIMEOUT_TICKS) return false;
        LOGGER.warn("[TaskExecutor] 任务超时: {}", task.getGoal());
        taskManager.cancelActiveTask();
        executionTick = 0;
        return true;
    }

    private void handleNoSubTask(Task task) {
        boolean anySucceeded = task.subTasks != null && task.subTasks.stream()
                .anyMatch(st -> "success".equals(st.status) || "accepted".equals(st.status));
        if (anySucceeded) {
            LOGGER.warn("[TaskExecutor] 子任务全部完成: {}", task.getGoal());
            taskManager.completeTask();
        } else {
            LOGGER.warn("[TaskExecutor] 无成功子任务，取消: {}", task.getGoal());
            taskManager.cancelActiveTask();
        }
        executionTick = 0;
    }

    private void executeOneSubTask(ServerPlayerEntity bot, Task task, Task.SubTask current) {
        Skill skill = skillManager.getSkill(current.skillId);
        if (skill == null) {
            LOGGER.warn("[TaskExecutor] 技能未找到: {}", current.skillId);
            current.status = "skipped";
            return;
        }

        Map<String, Object> context = new HashMap<>();
        context.put("goal", current.goal);
        context.put("task", task);

        try {
            Skill.SkillResult result = skill.execute(bot.getServerWorld(), bot, context);
            executionLogger.logAction(skill.getSkillId(), context,
                    result.success() ? "success" : "fail", result.effectiveness());

            if (result.success()) {
                handleSuccess(task, current, result);
            } else if (result.executed() && result.effectiveness() >= ACCEPTANCE_THRESHOLD) {
                handleAccepted(bot, task, current, result);
            } else {
                handleFailure(bot, task, current, result);
            }
        } catch (Exception e) {
            LOGGER.error("[TaskExecutor] 技能执行异常: " + current.skillId, e);
            executionLogger.logAction(current.skillId, context, "error", 0.0);
        }
    }

    private void handleSuccess(Task task, Task.SubTask current, Skill.SkillResult result) {
        current.status = "success";
        task.progress.completedCount++;
        taskManager.saveActiveTask();
        LOGGER.info("[TaskExecutor] 子任务完成: {} ({}/{})",
                current.goal, task.progress.completedCount, task.progress.targetCount);
        if (task.progress.completedCount >= task.progress.targetCount) {
            taskManager.completeTask();
            executionTick = 0;
        }
    }

    private void handleAccepted(ServerPlayerEntity bot, Task task, Task.SubTask current, Skill.SkillResult result) {
        current.status = "accepted";
        task.progress.completedCount++;
        taskManager.saveActiveTask();
        double drift = 1.0 - result.effectiveness();
        if (onAcceptDrift != null) onAcceptDrift.accept(drift);
        LOGGER.info("[TaskExecutor] 子任务接受(漂移{:.2f}): {} ({}/{})",
                drift, current.goal, task.progress.completedCount, task.progress.targetCount);
        if (task.progress.completedCount >= task.progress.targetCount) {
            taskManager.completeTask();
            executionTick = 0;
        }
    }

    private void handleFailure(ServerPlayerEntity bot, Task task, Task.SubTask current, Skill.SkillResult result) {
        if (!result.executed()) {
            current.unableCount++;
            if (current.unableCount >= MAX_UNABLE_RETRIES) {
                LOGGER.warn("[TaskExecutor] 连续无法执行{}次，跳过: {} (goal={})",
                        MAX_UNABLE_RETRIES, current.skillId, current.goal);
                current.status = "skipped";
                if (onUnableExhausted != null) onUnableExhausted.accept(bot);
            }
            return;
        }
        if (result.effectiveness() > 0.01) {
            current.attemptCount = 0;
        } else {
            current.attemptCount++;
        }
        int maxRetries = getMaxRetries();
        if (current.attemptCount >= maxRetries) {
            LOGGER.warn("[TaskExecutor] 子任务失败{}次: {} (goal={})",
                    maxRetries, current.skillId, current.goal);
            current.status = "skipped";
        }
    }

    private Task.SubTask getCurrentSubTask(Task task) {
        if (task.subTasks == null || task.subTasks.isEmpty()) return null;
        for (Task.SubTask st : task.subTasks) {
            if ("pending".equals(st.status) || "running".equals(st.status)) {
                st.status = "running";
                return st;
            }
        }
        return null;
    }

    public void setOnAcceptDrift(Consumer<Double> onAcceptDrift) {
        this.onAcceptDrift = onAcceptDrift;
    }

    public void setOnUnableExhausted(Consumer<ServerPlayerEntity> onUnableExhausted) {
        this.onUnableExhausted = onUnableExhausted;
    }

    public void resetExecutionTick() {
        executionTick = 0;
    }

    private int getMaxRetries() {
        return 3;
    }
}
