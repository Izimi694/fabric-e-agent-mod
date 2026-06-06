package com.izimi.aiplayermod.bot;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.api.*;
import com.izimi.aiplayermod.skill.ConditionedReflex;
import com.izimi.aiplayermod.state.StateManager;
import com.izimi.aiplayermod.task.TaskExecutor;
import com.izimi.aiplayermod.task.TaskManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class BotController {
    private final BotSpawner botSpawner;
    private final TaskManager taskManager;
    private final TaskExecutor taskExecutor;
    private final StateManager stateManager;
    private final ConditionedReflex conditionedReflex;
    private final AITaskPlanner aiTaskPlanner;
    private final AIChatHandler aiChatHandler;
    private final AIClient aiClient;

    private int tickCounter = 0;
    private int stateSaveInterval = 200;
    private int aiPollInterval = 20;

    public BotController(BotSpawner botSpawner, TaskManager taskManager,
                         TaskExecutor taskExecutor, StateManager stateManager,
                         ConditionedReflex conditionedReflex,
                         AITaskPlanner aiTaskPlanner, AIChatHandler aiChatHandler,
                         AIClient aiClient) {
        this.botSpawner = botSpawner;
        this.taskManager = taskManager;
        this.taskExecutor = taskExecutor;
        this.stateManager = stateManager;
        this.conditionedReflex = conditionedReflex;
        this.aiTaskPlanner = aiTaskPlanner;
        this.aiChatHandler = aiChatHandler;
        this.aiClient = aiClient;
    }

    public void onTick(MinecraftServer server) {
        tickCounter++;

        if (!botSpawner.isSpawned()) return;

        BotPlayer botPlayer = botSpawner.getBot();
        if (botPlayer == null) return;

        ServerPlayerEntity bot = botPlayer.asEntity();
        bot.tick();

        if (tickCounter % stateSaveInterval == 0) {
            stateManager.saveState(bot);
        }

        if (tickCounter % aiPollInterval == 0 && aiClient != null && aiClient.isConfigured()) {
            pollAIResults(bot);
        }

        var activeTask = taskManager.getActiveTask();
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            var reflexSkill = conditionedReflex.match(activeTask);
            if (reflexSkill != null) {
                conditionedReflex.executeReflex(reflexSkill, bot);
            } else {
                taskExecutor.executeTask(bot, activeTask);
            }
        }
    }

    private void pollAIResults(ServerPlayerEntity bot) {
        var chatHandler = AIPlayerMod.getAiChatHandler();
        if (chatHandler != null && chatHandler.hasPendingResponse()) {
            AIResponse response = chatHandler.pollResponse();
            if (response != null && response.isChat() && !response.getMessage().isEmpty()) {
                bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response.getMessage()));
                if (response.getMemoryNote() != null && !response.getMemoryNote().isEmpty()) {
                    var mem = AIPlayerMod.getMemoryManager();
                    if (mem != null) {
                        var entry = new com.izimi.aiplayermod.memory.MemoryEntry(
                                "mem_chat_" + System.currentTimeMillis(),
                                response.getMemoryNote());
                        mem.generateMemory(new com.izimi.aiplayermod.task.Task(
                                "chat", "instant", response.getMemoryNote()));
                    }
                }
                if (response.personalityDelta != null && !response.personalityDelta.isEmpty()) {
                    var charMgr = AIPlayerMod.getCharacterManager();
                    if (charMgr != null) {
                        charMgr.evolvePreferences(
                                new java.util.HashMap<>(),
                                new java.util.HashMap<>(),
                                response.personalityDelta);
                    }
                }
            }
        }

        var taskPlanner = AIPlayerMod.getAiTaskPlanner();
        if (taskPlanner != null && taskPlanner.hasPendingResult()) {
            AIResponse response = taskPlanner.pollResult();
            if (response != null) {
                processTaskResponse(bot, response);
            }
        }
    }

    private void processTaskResponse(ServerPlayerEntity bot, AIResponse response) {
        if (response.isChat()) {
            if (!response.getMessage().isEmpty()) {
                bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response.getMessage()));
            }
            return;
        }

        if (response.isAction()) {
            String goal = response.getAction() + " " + (response.getSkill() != null ? response.getSkill() : "");
            if (response.params != null && response.params.target != null) {
                goal = response.params.target;
                if (response.params.amount > 0) {
                    goal = "挖" + response.params.amount + "个" + goal;
                }
            }
            if (!"wait".equals(response.getAction()) && !goal.isEmpty()) {
                taskManager.createTask(goal);
                if (!response.getMessage().isEmpty()) {
                    bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response.getMessage()));
                }
            }
        }
    }
}
