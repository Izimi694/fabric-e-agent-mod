package com.izimi.aiplayermod.command;

import com.izimi.aiplayermod.AIPlayerMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AICommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("ai")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("§6[AI Player] §7使用 /ai <目标> 创建任务，或 /ai help 查看帮助"), false);
                    return 1;
                })
                .then(literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(literal("status").executes(ctx -> showStatus(ctx.getSource())))
                .then(literal("cancel").executes(ctx -> cancelTask(ctx.getSource())))
                .then(literal("resume").executes(ctx -> resumeTask(ctx.getSource())))
                .then(literal("explore").executes(ctx -> startExplore(ctx.getSource())))
                .then(literal("spawn").executes(ctx -> spawnBot(ctx.getSource())))
                .then(literal("despawn").executes(ctx -> despawnBot(ctx.getSource())))
                .then(literal("personality").executes(ctx -> showPersonality(ctx.getSource())))
                .then(literal("forget")
                        .then(argument("id", StringArgumentType.string()).executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            return forgetMemory(ctx.getSource(), id);
                        }))
                )
                .then(argument("goal", StringArgumentType.greedyString()).executes(ctx -> {
                    String goal = StringArgumentType.greedyString().getString(ctx, "goal");
                    return createTask(ctx.getSource(), goal);
                }));

        dispatcher.register(root);
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§6===== AI Player 指令帮助 ====="), false);
        source.sendFeedback(() -> Text.literal("§e/ai <目标> §7- 创建新任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai status §7- 查看当前任务状态"), false);
        source.sendFeedback(() -> Text.literal("§e/ai cancel §7- 中断当前任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai resume §7- 恢复上一个任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai explore §7- 开始自由探索"), false);
        source.sendFeedback(() -> Text.literal("§e/ai spawn §7- 生成AI玩家"), false);
        source.sendFeedback(() -> Text.literal("§e/ai despawn §7- 移除AI玩家"), false);
        source.sendFeedback(() -> Text.literal("§e/ai personality §7- 查看AI个性偏好"), false);
        source.sendFeedback(() -> Text.literal("§e/ai forget <id> §7- 删除指定记忆"), false);
        return 1;
    }

    private static int showStatus(ServerCommandSource source) {
        try {
            var taskManager = AIPlayerMod.getTaskManager();
            if (taskManager == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 任务系统未初始化"), false);
                return 0;
            }
            var activeTask = taskManager.getActiveTask();
            if (activeTask != null) {
                source.sendFeedback(() -> Text.literal("§a[AI Player] 当前任务: " + activeTask.getGoal()), false);
                source.sendFeedback(() -> Text.literal("§7  状态: " + activeTask.getStatus() + " | 进度: " + activeTask.getProgressSummary()), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 当前没有活动任务"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 获取状态失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int cancelTask(ServerCommandSource source) {
        try {
            var taskManager = AIPlayerMod.getTaskManager();
            if (taskManager == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 任务系统未初始化"), false);
                return 0;
            }
            taskManager.cancelActiveTask();
            source.sendFeedback(() -> Text.literal("§a[AI Player] 当前任务已中断，使用 /ai resume 恢复"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 中断失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int resumeTask(ServerCommandSource source) {
        try {
            var taskManager = AIPlayerMod.getTaskManager();
            if (taskManager == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 任务系统未初始化"), false);
                return 0;
            }
            boolean resumed = taskManager.resumeLastTask();
            if (resumed) {
                source.sendFeedback(() -> Text.literal("§a[AI Player] 已恢复上一个任务"), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 没有可恢复的任务"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 恢复失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int startExplore(ServerCommandSource source) {
        try {
            var taskManager = AIPlayerMod.getTaskManager();
            if (taskManager == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 任务系统未初始化"), false);
                return 0;
            }
            String taskId = taskManager.createExploreTask();
            source.sendFeedback(() -> Text.literal("§a[AI Player] 探索任务已创建: " + taskId), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 探索启动失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int createTask(ServerCommandSource source, String goal) {
        try {
            var taskManager = AIPlayerMod.getTaskManager();
            if (taskManager == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 任务系统未初始化"), false);
                return 0;
            }
            String taskId = taskManager.createTask(goal);
            source.sendFeedback(() -> Text.literal("§a[AI Player] 任务已创建: " + taskId), false);
            source.sendFeedback(() -> Text.literal("§7  目标: " + goal), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 任务创建失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int spawnBot(ServerCommandSource source) {
        try {
            var botSpawner = AIPlayerMod.getBotSpawner();
            if (botSpawner == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] Bot系统未初始化"), false);
                return 0;
            }
            var server = source.getServer();
            var world = source.getWorld();
            botSpawner.spawn(server, world, source.getPosition());
            source.sendFeedback(() -> Text.literal("§a[AI Player] Bot已生成"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] Bot生成失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int despawnBot(ServerCommandSource source) {
        try {
            var botSpawner = AIPlayerMod.getBotSpawner();
            if (botSpawner == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] Bot系统未初始化"), false);
                return 0;
            }
            botSpawner.despawn();
            source.sendFeedback(() -> Text.literal("§a[AI Player] Bot已移除"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] Bot移除失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showPersonality(ServerCommandSource source) {
        try {
            var characterManager = AIPlayerMod.getCharacterManager();
            if (characterManager == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 性格系统未初始化"), false);
                return 0;
            }
            var prefs = characterManager.getPreferences();
            if (prefs == null || prefs.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 暂无偏好数据"), false);
            } else {
                source.sendFeedback(() -> Text.literal("§6===== AI 个性偏好 ====="), false);
                for (var pref : prefs) {
                    String icon = pref.valence >= 0.5 ? "§a❤" : pref.valence <= -0.5 ? "§c✖" : "§7●";
                    source.sendFeedback(() -> Text.literal(icon + " §f" + pref.target + " §7(" + String.format("%.2f", pref.valence) + ")"), false);
                }
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 获取偏好失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int forgetMemory(ServerCommandSource source, String id) {
        try {
            var memoryManager = AIPlayerMod.getMemoryManager();
            if (memoryManager == null) {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 记忆系统未初始化"), false);
                return 0;
            }
            boolean deleted = memoryManager.deleteMemory(id);
            if (deleted) {
                source.sendFeedback(() -> Text.literal("§a[AI Player] 记忆已删除: " + id), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[AI Player] 未找到记忆: " + id), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[AI Player] 删除失败: " + e.getMessage()), false);
        }
        return 1;
    }
}
