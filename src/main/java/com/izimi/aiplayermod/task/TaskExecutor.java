package com.izimi.aiplayermod.task;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.log.ExecutionLogger;
import com.izimi.aiplayermod.skill.Skill;
import com.izimi.aiplayermod.skill.SkillManager;
import com.izimi.aiplayermod.state.StateManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;

public class TaskExecutor {
    private final TaskManager taskManager;
    private final SkillManager skillManager;
    private final StateManager stateManager;
    private final ExecutionLogger executionLogger;

    private int executionTick = 0;
    private static final int SKILL_TIMEOUT_TICKS = 6000;

    public TaskExecutor(TaskManager taskManager, SkillManager skillManager,
                        StateManager stateManager, ExecutionLogger executionLogger) {
        this.taskManager = taskManager;
        this.skillManager = skillManager;
        this.stateManager = stateManager;
        this.executionLogger = executionLogger;
    }

    public void executeTask(ServerPlayerEntity bot, Task task) {
        if (bot == null || task == null) return;

        executionTick++;

        if (executionTick > SKILL_TIMEOUT_TICKS) {
            AIPlayerMod.LOGGER.warn("[TaskExecutor] 任务超时: {}", task.getGoal());
            taskManager.cancelActiveTask();
            executionTick = 0;
            return;
        }

        String goal = task.getGoal();
        String skillId = matchSkillForGoal(goal);

        if (skillId == null) {
            AIPlayerMod.LOGGER.warn("[TaskExecutor] 无法匹配技能: {}", goal);
            taskManager.cancelActiveTask();
            return;
        }

        Skill skill = skillManager.getSkill(skillId);
        if (skill == null) {
            AIPlayerMod.LOGGER.warn("[TaskExecutor] 技能未找到: {}", skillId);
            return;
        }

        Map<String, Object> context = new HashMap<>();
        context.put("goal", goal);
        context.put("task", task);

        try {
            if (skill.canExecute(bot.getServerWorld(), bot, context)) {
                Skill.SkillResult result = skill.execute(bot.getServerWorld(), bot, context);

                executionLogger.logAction(
                        skill.getSkillId(),
                        context,
                        result.success() ? "success" : "fail",
                        result.effectiveness()
                );

                if (result.success()) {
                    taskManager.updateTaskProgress(skill.getSkillId(),
                            task.progress.completedCount + 1,
                            task.progress.targetCount);

                    if (task.progress.completedCount >= task.progress.targetCount) {
                        taskManager.completeTask();
                        executionTick = 0;
                    }
                }
            }
        } catch (Exception e) {
            AIPlayerMod.LOGGER.error("[TaskExecutor] 技能执行异常: " + skillId, e);
            executionLogger.logAction(skillId, context, "error", 0.0);
        }
    }

    public void resetExecutionTick() {
        executionTick = 0;
    }

    private String matchSkillForGoal(String goal) {
        if (goal == null) return null;

        if (containsAny(goal, "挖", "矿", "采集", "收集", "砍", "伐", "mine", "dig", "collect")) {
            return "dig";
        }
        if (containsAny(goal, "攻击", "杀", "打", "击败", "消灭", "attack", "kill", "fight")) {
            return "attack";
        }
        if (containsAny(goal, "合成", "制作", "造", "做", "craft", "make", "build")) {
            return "craft";
        }
        if (containsAny(goal, "去", "到", "走", "移动", "探索", "探", "逛", "move", "go", "explore", "walk")) {
            return "move";
        }

        return "move";
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword)) return true;
        }
        return false;
    }
}
