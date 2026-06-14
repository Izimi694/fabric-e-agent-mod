package com.izimi.eagent.command;

import com.izimi.eagent.EAgent;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.brainstem.bot.BotInstance;
import com.izimi.eagent.brainstem.bot.BotManager;
import com.izimi.eagent.brainstem.bot.ReflexPackManager;
import com.izimi.eagent.cortex.api.PersonaManager;
import com.izimi.eagent.cortex.api.PlaystylePack;
import com.izimi.eagent.util.FileUtil;
import com.izimi.eagent.util.JsonUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Path;
import java.util.Map;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class AICommand {

    private static WorldContext worldContext;

    public static void setWorldContext(WorldContext ctx) {
        worldContext = ctx;
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root = literal("ai")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("§6[E-Agent] §7使用 /ai <目标> 创建任务，或 /ai help 查看帮助"), false);
                    return 1;
                })
                .then(literal("help").executes(ctx -> showHelp(ctx.getSource())))
                .then(literal("status").executes(ctx -> showStatus(ctx.getSource(), null)))
                .then(literal("cancel").executes(ctx -> cancelTask(ctx.getSource(), null)))
                .then(literal("resume").executes(ctx -> resumeTask(ctx.getSource(), null)))
                .then(literal("explore").executes(ctx -> startExplore(ctx.getSource(), null)))
                .then(literal("spawn")
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return spawnBot(ctx.getSource(), name);
                        }))
                        .executes(ctx -> spawnBot(ctx.getSource(), "E-Agent"))
                )
                .then(literal("despawn")
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return despawnBot(ctx.getSource(), name);
                        }))
                        .executes(ctx -> despawnBot(ctx.getSource(), null))
                )
                .then(literal("list").executes(ctx -> listBots(ctx.getSource())))
                .then(literal("personality")
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return showPersonality(ctx.getSource(), name);
                        }))
                        .executes(ctx -> showPersonality(ctx.getSource(), null))
                )
                .then(literal("persona")
                        .then(literal("list").executes(ctx -> listPersonas(ctx.getSource())))
                        .then(argument("name", StringArgumentType.string()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return loadPersona(ctx.getSource(), name);
                        }))
                        .executes(ctx -> showCurrentPersona(ctx.getSource()))
                )
                .then(literal("suggestions")
                        .then(argument("name", StringArgumentType.word()).executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            return triggerSuggestions(ctx.getSource(), name);
                        }))
                        .executes(ctx -> triggerSuggestions(ctx.getSource(), null))
                )
                .then(literal("playstyle")
                        .then(literal("list").executes(ctx -> listPlaystylePacks(ctx.getSource())))
                        .then(literal("load")
                                .then(argument("packName", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String packName = StringArgumentType.getString(ctx, "packName");
                                            return loadPlaystylePack(ctx.getSource(), packName, null);
                                        })
                                        .then(argument("botName", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String packName = StringArgumentType.getString(ctx, "packName");
                                                    String botName = StringArgumentType.getString(ctx, "botName");
                                                    return loadPlaystylePack(ctx.getSource(), packName, botName);
                                                })
                                        )
                                )
                        )
                        .then(literal("export")
                                .then(argument("botName", StringArgumentType.word())
                                        .then(argument("packName", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String botName = StringArgumentType.getString(ctx, "botName");
                                                    String packName = StringArgumentType.getString(ctx, "packName");
                                                    return exportPlaystylePack(ctx.getSource(), botName, packName);
                                                })
                                        )
                                )
                        )
                        .then(literal("current")
                                .then(argument("botName", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String botName = StringArgumentType.getString(ctx, "botName");
                                            return showCurrentPlaystyle(ctx.getSource(), botName);
                                        })
                                )
                                .executes(ctx -> showCurrentPlaystyle(ctx.getSource(), null))
                        )
                )
                .then(literal("forget")
                        .then(argument("id", StringArgumentType.string()).executes(ctx -> {
                            String id = StringArgumentType.getString(ctx, "id");
                            return forgetMemory(ctx.getSource(), id, null);
                        }))
                )
                .then(literal("setkey")
                        .then(argument("key", StringArgumentType.greedyString()).executes(ctx -> {
                            String key = StringArgumentType.getString(ctx, "key");
                            return setApiKey(ctx.getSource(), key);
                        }))
                )
                .then(literal("apikey").executes(ctx -> showApiKeyStatus(ctx.getSource())))
                .then(literal("nick")
                        .then(argument("name", StringArgumentType.word())
                                .then(argument("nickname", StringArgumentType.greedyString()).executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    String nickname = StringArgumentType.getString(ctx, "nickname");
                                    return setNickname(ctx.getSource(), name, nickname);
                                }))
                        )
                )
                .then(literal("model")
                        .executes(ctx -> showCurrentModel(ctx.getSource()))
                        .then(argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    builder.suggest("deepseek-chat");
                                    builder.suggest("deepseek-v4-flash");
                                    builder.suggest("deepseek-reasoner");
                                    return builder.buildFuture();
                                })
                                .executes(ctx -> {
                                    String modelName = StringArgumentType.getString(ctx, "name");
                                    return setApiModel(ctx.getSource(), modelName);
                                }))
                )
                .then(literal("reflexpack")
                        .then(literal("export")
                                .then(argument("botName", StringArgumentType.word())
                                        .then(argument("packName", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String botName = StringArgumentType.getString(ctx, "botName");
                                                    String packName = StringArgumentType.getString(ctx, "packName");
                                                    return exportReflexPack(ctx.getSource(), botName, packName, true);
                                                })
                                                .then(literal("noprior")
                                                        .executes(ctx -> {
                                                            String botName = StringArgumentType.getString(ctx, "botName");
                                                            String packName = StringArgumentType.getString(ctx, "packName");
                                                            return exportReflexPack(ctx.getSource(), botName, packName, false);
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(literal("import")
                                .then(argument("botName", StringArgumentType.word())
                                        .then(argument("packName", StringArgumentType.word())
                                                .executes(ctx -> {
                                                    String botName = StringArgumentType.getString(ctx, "botName");
                                                    String packName = StringArgumentType.getString(ctx, "packName");
                                                    return importReflexPack(ctx.getSource(), botName, packName, false);
                                                })
                                                .then(literal("reset")
                                                        .executes(ctx -> {
                                                            String botName = StringArgumentType.getString(ctx, "botName");
                                                            String packName = StringArgumentType.getString(ctx, "packName");
                                                            return importReflexPack(ctx.getSource(), botName, packName, true);
                                                        })
                                                )
                                        )
                                )
                        )
                        .then(literal("list")
                                .executes(ctx -> listReflexPacks(ctx.getSource()))
                        )
                        .then(literal("delete")
                                .then(argument("packName", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String packName = StringArgumentType.getString(ctx, "packName");
                                            return deleteReflexPack(ctx.getSource(), packName);
                                        })
                                )
                        )
                )
                .then(literal("bot")
                        .then(argument("botName", StringArgumentType.word())
                                .then(literal("status").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return showStatus(ctx.getSource(), botName);
                                }))
                                .then(literal("cancel").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return cancelTask(ctx.getSource(), botName);
                                }))
                                .then(literal("resume").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return resumeTask(ctx.getSource(), botName);
                                }))
                                .then(literal("explore").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return startExplore(ctx.getSource(), botName);
                                }))
                                .then(literal("personality").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return showPersonality(ctx.getSource(), botName);
                                }))
                                .then(literal("suggestions").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return triggerSuggestions(ctx.getSource(), botName);
                                }))
                                .then(literal("despawn").executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    return despawnBot(ctx.getSource(), botName);
                                }))
                                .then(argument("goal", StringArgumentType.greedyString()).executes(ctx -> {
                                    String botName = StringArgumentType.getString(ctx, "botName");
                                    String goal = StringArgumentType.getString(ctx, "goal");
                                    return createTaskForBot(ctx.getSource(), botName, goal);
                                }))
                        )
                )
                .then(argument("goal", StringArgumentType.greedyString()).executes(ctx -> {
                    String goal = StringArgumentType.getString(ctx, "goal");
                    return createTask(ctx.getSource(), goal);
                }));

        dispatcher.register(root);
    }

    private static int showHelp(ServerCommandSource source) {
        source.sendFeedback(() -> Text.literal("§6===== AI Player 指令帮助 ====="), false);
        source.sendFeedback(() -> Text.literal("§e/ai <目标> §7- 创建新任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai spawn <名字> §7- 生成指定名字的AI"), false);
        source.sendFeedback(() -> Text.literal("§e/ai despawn [名字] §7- 移除AI"), false);
        source.sendFeedback(() -> Text.literal("§e/ai list §7- 列出所有AI"), false);
        source.sendFeedback(() -> Text.literal("§e/ai status §7- 查看当前任务状态"), false);
        source.sendFeedback(() -> Text.literal("§e/ai cancel §7- 中断当前任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai resume §7- 恢复上一个任务"), false);
        source.sendFeedback(() -> Text.literal("§e/ai explore §7- 开始自由探索"), false);
        source.sendFeedback(() -> Text.literal("§e/ai personality [名字] §7- 查看AI个性偏好"), false);
        source.sendFeedback(() -> Text.literal("§e/ai suggestions [名字] §7- 触发AI主动建议"), false);
        source.sendFeedback(() -> Text.literal("§e/ai forget <id> §7- 删除指定记忆"), false);
        source.sendFeedback(() -> Text.literal("§e/ai setkey <key> §7- 设置AI API密钥"), false);
        source.sendFeedback(() -> Text.literal("§e/ai apikey §7- 查看API密钥状态"), false);
        source.sendFeedback(() -> Text.literal("§e/ai persona list §7- 列出可用角色档案"), false);
        source.sendFeedback(() -> Text.literal("§e/ai persona <名称> §7- 加载角色 (艾露猫/傲娇大小姐/好兄弟)"), false);
        source.sendFeedback(() -> Text.literal("§e/ai persona §7- 查看当前角色"), false);
        source.sendFeedback(() -> Text.literal("§e/ai model [name] §7- 查看/设置AI模型 (如 deepseek-chat)"), false);
        source.sendFeedback(() -> Text.literal("§e/ai bot <名字> <指令> §7- 指定AI执行指令"), false);
        source.sendFeedback(() -> Text.literal("§e/ai nick <名字> <小名> §7- 设置AI的小名/昵称"), false);
        source.sendFeedback(() -> Text.literal("§e/ai reflexpack export <机器人> <包名> §7- 导出反射 (默认含先验)"), false);
        source.sendFeedback(() -> Text.literal("§e/ai reflexpack export <机器人> <包名> noprior §7- 导出反射 (不含先验)"), false);
        source.sendFeedback(() -> Text.literal("§e/ai reflexpack import <机器人> <包名> [reset] §7- 导入反射 (合并/冷启动)"), false);
        source.sendFeedback(() -> Text.literal("§e/ai reflexpack list §7- 列出已导入反射包"), false);
        source.sendFeedback(() -> Text.literal("§e/ai reflexpack delete <包名> §7- 删除反射包"), false);
        source.sendFeedback(() -> Text.literal("§e/ai playstyle list §7- 列出可用玩法包"), false);
        source.sendFeedback(() -> Text.literal("§e/ai playstyle load <包名> [机器人] §7- 加载玩法包"), false);
        source.sendFeedback(() -> Text.literal("§e/ai playstyle export <机器人> <包名> §7- 导出当前状态为玩法包"), false);
        source.sendFeedback(() -> Text.literal("§e/ai playstyle current [机器人] §7- 查看当前玩法信息"), false);
        return 1;
    }

    private static BotManager getManager() {
        return worldContext.botManager();
    }

    private static BotInstance resolveBot(ServerCommandSource source, String name) {
        BotManager mgr = getManager();
        if (mgr == null || mgr.isEmpty()) return null;
        if (name != null) return mgr.getByName(name);
        // Nearest to command sender
        ServerPlayerEntity player = source.getPlayer();
        if (player != null) return mgr.getNearest(player);
        // Fallback: first bot
        return mgr.getAll().get(0);
    }

    private static int showStatus(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }
            var taskManager = bot.getTaskManager();
            var activeTask = taskManager.getActiveTask();
            if (activeTask != null) {
                source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 当前任务: " + activeTask.getGoal()), false);
                source.sendFeedback(() -> Text.literal("§7  状态: " + activeTask.getStatus() + " | 进度: " + activeTask.getProgressSummary()), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[" + bot.getBotName() + "] 当前没有活动任务"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 获取状态失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int cancelTask(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }
            bot.getTaskManager().cancelActiveTask();
            source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 任务已中断"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 中断失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int resumeTask(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }
            boolean resumed = bot.getTaskManager().resumeLastTask();
            if (resumed) {
                source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 已恢复上一个任务"), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[" + bot.getBotName() + "] 没有可恢复的任务"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 恢复失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int startExplore(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }
            String taskId = bot.getTaskManager().createExploreTask();
            source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 探索任务已创建: " + taskId), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 探索启动失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int createTask(ServerCommandSource source, String goal) {
        BotInstance bot = resolveBot(source, null);
        if (bot == null) {
            source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
            return 0;
        }
        return createTaskForBot(source, bot.getBotName(), goal);
    }

    private static int createTaskForBot(ServerCommandSource source, String botName, String goal) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 未找到AI: " + botName), false);
                return 0;
            }
            String taskId = bot.getTaskManager().createTask(goal);
            source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 任务已创建: " + taskId), false);
            source.sendFeedback(() -> Text.literal("§7  目标: " + goal), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 任务创建失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int spawnBot(ServerCommandSource source, String name) {
        try {
            BotManager mgr = getManager();
            if (mgr == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] Bot系统未初始化"), false);
                return 0;
            }

            Vec3d spawnPos = source.getPosition();
            ServerPlayerEntity player = source.getPlayer();
            if (player != null) {
                // Player's view direction + 3 blocks forward
                Vec3d look = player.getRotationVector();
                spawnPos = player.getPos().add(look.x * 3, 0, look.z * 3);
                // Find ground
                BlockPos ground = findGroundBelow(player.getServerWorld(), spawnPos);
                if (ground != null) {
                    spawnPos = new Vec3d(spawnPos.x, ground.getY() + 1, spawnPos.z);
                }
            }

            var server = source.getServer();
            var world = source.getWorld();
            mgr.spawn(name, server, world, spawnPos);
            source.sendFeedback(() -> Text.literal("§a[E-Agent] AI已生成: " + name), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 生成失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int despawnBot(ServerCommandSource source, String name) {
        try {
            BotManager mgr = getManager();
            if (mgr == null || mgr.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }

            if (name != null) {
                BotInstance bot = mgr.getByName(name);
                if (bot == null) {
                    source.sendFeedback(() -> Text.literal("§7[E-Agent] 未找到AI: " + name), false);
                    return 0;
                }
                mgr.despawn(bot.getBotId());
                source.sendFeedback(() -> Text.literal("§a[E-Agent] AI已移除: " + name), false);
            } else {
                // Despawn nearest
                BotInstance nearest = resolveBot(source, null);
                if (nearest != null) {
                    mgr.despawn(nearest.getBotId());
                    source.sendFeedback(() -> Text.literal("§a[E-Agent] AI已移除: " + nearest.getBotName()), false);
                }
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 移除失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int listBots(ServerCommandSource source) {
        try {
            BotManager mgr = getManager();
            if (mgr == null || mgr.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 当前没有活动的AI"), false);
                return 0;
            }
            source.sendFeedback(() -> Text.literal("§6===== AI列表 (" + mgr.getCount() + "个) ====="), false);
            for (BotInstance bot : mgr.getAll()) {
                boolean hasTask = bot.getTaskManager().getActiveTask() != null;
                String status = hasTask ? "§a[工作中]" : "§7[空闲]";
                source.sendFeedback(() -> Text.literal("§e" + bot.getBotName() + " §f" + status), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showPersonality(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }

            var botParams = bot.getBotParams();
            source.sendFeedback(() -> Text.literal("§6===== " + bot.getBotName() + " 参数 ====="), false);
            source.sendFeedback(() -> Text.literal("§e学习速率(α): §f" + String.format("%.3f", botParams.getAlpha()) + " §7(0.1~0.6)"), false);
            source.sendFeedback(() -> Text.literal("§e固执程度(β): §f" + String.format("%.4f", botParams.getBeta()) + " §7(0.002~0.03)"), false);

            var skillManager = worldContext.skillManager();
            if (skillManager != null) {
                var skills = skillManager.getSkills();
                long conditionedCount = skills.values().stream()
                        .filter(s -> "conditioned".equals(s.getType())).count();
                source.sendFeedback(() -> Text.literal("§e已学习反射: §f" + conditionedCount + "个"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 获取参数失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int loadPersona(ServerCommandSource source, String name) {
        try {
            PersonaManager pm = EAgent.getPersonaManager();
            if (pm == null) {
                source.sendFeedback(() -> Text.literal("§c[E-Agent] PersonaManager 未初始化"), false);
                return 0;
            }
            pm.loadProfile(name);
            pm.setPersonaLocked(true);
            source.sendFeedback(() -> Text.literal("§a[E-Agent] 已加载角色: " + name + " (已锁定，玩法包不会覆盖)"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 加载角色失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int listPersonas(ServerCommandSource source) {
        try {
            var dir = com.izimi.eagent.util.FileUtil.getCharacterDir().resolve("personas");
            if (!java.nio.file.Files.exists(dir)) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有可用角色档案。请将 .json 放到 eagent/skills/character/personas/ 目录下"), false);
                return 1;
            }
            var names = new java.util.ArrayList<String>();
            try (var stream = java.nio.file.Files.list(dir)) {
                stream.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                    names.add(p.getFileName().toString().replace(".json", ""));
                });
            }
            if (names.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有可用角色档案"), false);
                return 1;
            }
            source.sendFeedback(() -> Text.literal("§6可用角色: §f" + String.join(", ", names)), false);
            source.sendFeedback(() -> Text.literal("§7使用 /ai persona <名称> 加载"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 列出角色失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showCurrentPersona(ServerCommandSource source) {
        PersonaManager pm = EAgent.getPersonaManager();
        if (pm == null || !pm.hasPersona()) {
            source.sendFeedback(() -> Text.literal("§7[E-Agent] 当前没有活跃角色设定"), false);
            return 1;
        }
        String persona = pm.getPersona();
        int overrides = pm.getActiveOverrides().size();
        source.sendFeedback(() -> Text.literal("§6当前角色: §f" + persona.substring(0, Math.min(persona.length(), 60)) + (persona.length() > 60 ? "..." : "")), false);
        source.sendFeedback(() -> Text.literal("§7本地覆盖: " + overrides + " 条"), false);
        return 1;
    }

    private static int forgetMemory(ServerCommandSource source, String id, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }
            boolean deleted = bot.getMemoryManager().deleteMemory(id);
            if (deleted) {
                source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 记忆已删除: " + id), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[" + bot.getBotName() + "] 未找到记忆: " + id), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 删除失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int triggerSuggestions(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null || !bot.isSpawned()) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }

            var suggestion = bot.getIdleBrain().forceSuggest();
            if (suggestion != null) {
                bot.sendMessage(suggestion.text());
                source.sendFeedback(() -> Text.literal("§a[" + bot.getBotName() + "] 建议已发送: " + suggestion.text()), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[" + bot.getBotName() + "] 当前没有可用的建议模板"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 建议触发失败: " + e.getMessage()), false);
        }
        return 1;
    }

    // ── ReflexPack command handlers ──

    private static BotInstance resolveBotForPack(ServerCommandSource source, String botName) {
        BotManager mgr = getManager();
        if (mgr == null || mgr.isEmpty()) return null;
        if (botName != null) return mgr.getByName(botName);
        BotInstance bot = resolveBot(source, null);
        return bot;
    }

    private static int exportReflexPack(ServerCommandSource source, String botName, String packName, boolean includePrior) {
        try {
            BotInstance bot = resolveBotForPack(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[ReflexPack] 没有活动的AI"), false);
                return 0;
            }
            boolean ok = bot.getReflexPackManager().exportPack(packName, includePrior);
            if (ok) {
                source.sendFeedback(() -> Text.literal("§a[ReflexPack] 导出成功: " + packName
                        + (includePrior ? " (含先验)" : " (不含先验)")), false);
            } else {
                source.sendFeedback(() -> Text.literal("§c[ReflexPack] 导出失败: " + packName), false);
            }
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[ReflexPack] 导出异常: " + e.getMessage()), false);
            return 0;
        }
    }

    private static int importReflexPack(ServerCommandSource source, String botName, String packName, boolean reset) {
        if (!source.hasPermissionLevel(2)) {
            source.sendFeedback(() -> Text.literal("§c[ReflexPack] 仅 OP 可导入反射包"), false);
            return 0;
        }
        try {
            BotInstance bot = resolveBotForPack(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[ReflexPack] 没有活动的AI"), false);
                return 0;
            }
            boolean ok = bot.getReflexPackManager().importPack(packName, reset);
            if (ok) {
                source.sendFeedback(() -> Text.literal("§a[ReflexPack] 导入成功: " + packName
                        + (reset ? " (冷启动)" : " (合并)")), false);
            } else {
                source.sendFeedback(() -> Text.literal("§c[ReflexPack] 导入失败: " + packName), false);
            }
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[ReflexPack] 导入异常: " + e.getMessage()), false);
            return 0;
        }
    }

    private static int listReflexPacks(ServerCommandSource source) {
        try {
            var packs = ReflexPackManager.listPacks();
            if (packs.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[ReflexPack] 没有已导入的反射包"), false);
                return 0;
            }
            source.sendFeedback(() -> Text.literal("§6===== 反射包列表 (" + packs.size() + "个) ====="), false);
            for (String desc : packs) {
                source.sendFeedback(() -> Text.literal("§e  " + desc), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[ReflexPack] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int deleteReflexPack(ServerCommandSource source, String packName) {
        if (!source.hasPermissionLevel(2)) {
            source.sendFeedback(() -> Text.literal("§c[ReflexPack] 仅 OP 可删除反射包"), false);
            return 0;
        }
        try {
            boolean ok = ReflexPackManager.deletePack(packName);
            if (ok) {
                source.sendFeedback(() -> Text.literal("§a[ReflexPack] 删除成功: " + packName), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[ReflexPack] 包不存在: " + packName), false);
            }
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[ReflexPack] 删除异常: " + e.getMessage()), false);
            return 0;
        }
    }

    // ── Playstyle command handlers ──

    @SuppressWarnings("unchecked")
    private static int loadPlaystylePack(ServerCommandSource source, String packName, String botName) {
        if (!source.hasPermissionLevel(2)) {
            source.sendFeedback(() -> Text.literal("§c[Playstyle] 仅 OP 可加载玩法包"), false);
            return 0;
        }
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[Playstyle] 没有活动的AI"), false);
                return 0;
            }

            Path packFile = FileUtil.getReflexPacksDir().resolve(packName + ".json");
            if (!java.nio.file.Files.exists(packFile)) {
                source.sendFeedback(() -> Text.literal("§7[Playstyle] 包不存在: " + packName), false);
                return 0;
            }

            Map<String, Object> data = JsonUtil.readMapFromFileSafe(packFile);
            if (data == null) {
                source.sendFeedback(() -> Text.literal("§c[Playstyle] 无法读取包: " + packName), false);
                return 0;
            }

            int ver = data.containsKey("version") ? ((Number) data.get("version")).intValue() : 1;
            if (ver < 2) {
                source.sendFeedback(() -> Text.literal("§e[Playstyle] 包是V1格式，使用旧导入路径。"), false);
                return bot.getReflexPackManager().importPack(packName, false) ? 1 : 0;
            }

            // Parse V2 pack
            Map<String, Object> profileMap = (Map<String, Object>) data.get("profile");
            PlaystylePack.PackProfile profile = profileMap != null ? ReflexPackManager.parseProfile(profileMap) : null;
            Map<String, Object> reflexes = (Map<String, Object>) data.get("reflexes");
            Map<String, Object> knowledge = (Map<String, Object>) data.get("knowledge");
            Map<String, Object> config = (Map<String, Object>) data.get("config");

            PlaystylePack pack = new PlaystylePack(packName,
                (String) data.getOrDefault("description", ""), 2,
                profile, reflexes, knowledge, config,
                (String) data.getOrDefault("persona", ""));

            boolean ok = bot.applyPlaystylePack(pack);
            if (ok) {
                source.sendFeedback(() -> Text.literal("§a[Playstyle] 玩法包已加载: " + packName), false);
                // 自动切换人设 (仅当未被手动锁定)
                String personaName = pack.persona();
                if (!personaName.isEmpty()) {
                    PersonaManager pm = EAgent.getPersonaManager();
                    if (pm != null && !pm.isPersonaLocked()) {
                        pm.loadProfile(personaName);
                        source.sendFeedback(() -> Text.literal("§7  └ 自动切换人设: " + personaName), false);
                    }
                }
            } else {
                source.sendFeedback(() -> Text.literal("§c[Playstyle] 加载失败: " + packName), false);
            }
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Playstyle] 加载异常: " + e.getMessage()), false);
            return 0;
        }
    }

    private static int listPlaystylePacks(ServerCommandSource source) {
        try {
            var packs = ReflexPackManager.listPacks();
            if (packs.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[Playstyle] 没有可用玩法包"), false);
                return 0;
            }
            source.sendFeedback(() -> Text.literal("§6===== 玩法包列表 (" + packs.size() + "个) ====="), false);
            for (String desc : packs) {
                source.sendFeedback(() -> Text.literal("§e  " + desc), false);
            }
            source.sendFeedback(() -> Text.literal("§7使用 /ai playstyle load <包名> [机器人] 加载"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Playstyle] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int exportPlaystylePack(ServerCommandSource source, String botName, String packName) {
        if (!source.hasPermissionLevel(2)) {
            source.sendFeedback(() -> Text.literal("§c[Playstyle] 仅 OP 可导出玩法包"), false);
            return 0;
        }
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[Playstyle] 没有活动的AI"), false);
                return 0;
            }

            Path packFile = FileUtil.getReflexPacksDir().resolve(packName + ".json");
            Map<String, Object> pack = new java.util.LinkedHashMap<>();
            pack.put("version", 2);
            pack.put("source", "E-Agent");
            pack.put("source_bot", bot.getBotId().toString());
            pack.put("packName", packName);
            pack.put("description", "");
            pack.put("exported_at", java.time.Instant.now().toString());

            // Profile
            var bp = bot.getBotParams();
            var h = bot.getHormonalSystem();
            Map<String, Object> profile = new java.util.LinkedHashMap<>();
            profile.put("alpha", bp.getAlpha());
            profile.put("beta", bp.getBeta());
            profile.put("temperature", bp.getTemperature());
            Map<String, Object> hp = new java.util.LinkedHashMap<>();
            hp.put("stress", h.getStress());
            hp.put("aggression", h.getAggression());
            hp.put("curiosity", h.getCuriosity());
            hp.put("ne", h.getNE());
            hp.put("da", h.getDA());
            hp.put("serotonin", h.getSerotonin());
            hp.put("ach", h.getACh());
            profile.put("hormonal_preset", hp);
            var weights = bot.getMotivationEngine().getPerspectiveWeights();
            if (weights != null) profile.put("perspective_weights", weights);
            pack.put("profile", profile);

            // Reflexes
            Path conditionedDir = FileUtil.getBotConditionedDir(bot.getBotId());
            Map<String, Object> reflexes = new java.util.LinkedHashMap<>();
            if (java.nio.file.Files.exists(conditionedDir)) {
                try (var stream = java.nio.file.Files.list(conditionedDir)) {
                    for (Path rf : stream.toList()) {
                        if (!rf.toString().endsWith(".json")) continue;
                        Map<String, Object> rd = JsonUtil.readMapFromFileSafe(rf);
                        if (rd != null) {
                            String sid = (String) rd.getOrDefault("skillId",
                                rf.getFileName().toString().replace(".json", ""));
                            reflexes.put(sid, rd);
                        }
                    }
                }
            }
            pack.put("reflexes", reflexes);
            pack.put("knowledge", new java.util.LinkedHashMap<>());
            pack.put("config", new java.util.LinkedHashMap<>());

            JsonUtil.writeToFileSafeAtomic(packFile, pack);
            source.sendFeedback(() -> Text.literal("§a[Playstyle] 已导出: " + packName + " (" + reflexes.size() + " 反射)"), false);
            return 1;
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Playstyle] 导出异常: " + e.getMessage()), false);
            return 0;
        }
    }

    private static int showCurrentPlaystyle(ServerCommandSource source, String botName) {
        try {
            BotInstance bot = resolveBot(source, botName);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[Playstyle] 没有活动的AI"), false);
                return 0;
            }
            var bp = bot.getBotParams();
            source.sendFeedback(() -> Text.literal("§6===== " + bot.getBotName() + " 当前参数 ====="), false);
            source.sendFeedback(() -> Text.literal("§eα=§f" + String.format("%.3f", bp.getAlpha())
                    + " §eβ=§f" + String.format("%.4f", bp.getBeta())
                    + " §e温度=§f" + String.format("%.3f", bp.getTemperature())), false);
            var h = bot.getHormonalSystem();
            source.sendFeedback(() -> Text.literal("§e激素: §fNE=" + String.format("%.2f", h.getNE())
                    + " DA=" + String.format("%.2f", h.getDA())
                    + " 5-HT=" + String.format("%.2f", h.getSerotonin())
                    + " ACh=" + String.format("%.2f", h.getACh())), false);
            var skillMgr = worldContext.skillManager();
            long count = skillMgr != null ? skillMgr.getSkills().values().stream()
                .filter(s -> "conditioned".equals(s.getType())).count() : 0L;
            long reflexCount = count;
            source.sendFeedback(() -> Text.literal("§e已学习反射: §f" + reflexCount + " 个"), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[Playstyle] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int setNickname(ServerCommandSource source, String name, String nickname) {
        try {
            BotManager mgr = getManager();
            if (mgr == null || mgr.isEmpty()) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 没有活动的AI"), false);
                return 0;
            }
            BotInstance bot = mgr.getByName(name);
            if (bot == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 未找到AI: " + name), false);
                return 0;
            }
            bot.setNickname(nickname);
            source.sendFeedback(() -> Text.literal("§a[E-Agent] " + bot.getBotName() + " 的小名已设置为: " + nickname), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 设置小名失败: " + e.getMessage()), false);
        }
        return 1;
    }

    // Shared utility methods
    private static int setApiKey(ServerCommandSource source, String key) {
        try {
            var aiClient = worldContext.cortex().aiClient();
            if (aiClient == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] AI客户端未初始化"), false);
                return 0;
            }
            if ("clear".equalsIgnoreCase(key.trim())) {
                aiClient.setApiKey("");
                source.sendFeedback(() -> Text.literal("§a[E-Agent] API密钥已清除，退回规则引擎模式"), false);
            } else {
                aiClient.setApiKey(key.trim());
                source.sendFeedback(() -> Text.literal("§a[E-Agent] API密钥已设置，正在测试连接..."), false);
                boolean ok = aiClient.testConnection();
                if (ok) {
                    source.sendFeedback(() -> Text.literal("§a[E-Agent] 连接成功! AI模式已激活"), false);
                } else {
                    source.sendFeedback(() -> Text.literal("§c[E-Agent] 连接失败，请检查密钥是否正确"), false);
                }
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 设置失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showApiKeyStatus(ServerCommandSource source) {
        try {
            var aiClient = worldContext.cortex().aiClient();
            if (aiClient == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] AI客户端未初始化"), false);
                return 0;
            }
            if (aiClient.isConfigured()) {
                source.sendFeedback(() -> Text.literal("§a[E-Agent] AI模式已激活 (DeepSeek)"), false);
                source.sendFeedback(() -> Text.literal("§7  使用 /ai setkey clear 可清除密钥，退回规则引擎"), false);
            } else {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] 规则引擎模式"), false);
                source.sendFeedback(() -> Text.literal("§7  使用 /ai setkey <key> 设置API密钥启用AI"), false);
            }
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int showCurrentModel(ServerCommandSource source) {
        try {
            var config = com.izimi.eagent.cortex.api.AIConfig.load();
            source.sendFeedback(() -> Text.literal("§e[E-Agent] 当前模型: §f" + config.apiModel), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 查询失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static int setApiModel(ServerCommandSource source, String modelName) {
        try {
            var aiClient = worldContext.cortex().aiClient();
            if (aiClient == null) {
                source.sendFeedback(() -> Text.literal("§7[E-Agent] AI客户端未初始化"), false);
                return 0;
            }
            aiClient.setApiModel(modelName);
            source.sendFeedback(() -> Text.literal("§a[E-Agent] AI模型已设置为: §f" + modelName), false);
        } catch (Exception e) {
            source.sendFeedback(() -> Text.literal("§c[E-Agent] 设置失败: " + e.getMessage()), false);
        }
        return 1;
    }

    private static BlockPos findGroundBelow(net.minecraft.server.world.ServerWorld world, Vec3d pos) {
        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int y = (int) Math.floor(pos.y);
        for (int dy = 0; dy > -20; dy--) {
            BlockPos check = new BlockPos(x, y + dy, z);
            var state = world.getBlockState(check);
            if (state.isSolidBlock(world, check)) {
                return check.up();
            }
        }
        return new BlockPos(x, y, z);
    }
}
