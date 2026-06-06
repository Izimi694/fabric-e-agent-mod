package com.izimi.aiplayermod;

import com.izimi.aiplayermod.bot.BotSpawner;
import com.izimi.aiplayermod.bot.BotController;
import com.izimi.aiplayermod.character.CharacterManager;
import com.izimi.aiplayermod.character.BehaviorObserver;
import com.izimi.aiplayermod.command.AICommand;
import com.izimi.aiplayermod.config.ModConfig;
import com.izimi.aiplayermod.memory.MemoryManager;
import com.izimi.aiplayermod.memory.MemoryQuery;
import com.izimi.aiplayermod.state.StateManager;
import com.izimi.aiplayermod.task.TaskManager;
import com.izimi.aiplayermod.task.TaskExecutor;
import com.izimi.aiplayermod.skill.SkillManager;
import com.izimi.aiplayermod.skill.ConditionedReflex;
import com.izimi.aiplayermod.log.ExecutionLogger;
import com.izimi.aiplayermod.util.FileUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AIPlayerMod implements ModInitializer {
    public static final String MOD_ID = "ai-player-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ModConfig config;
    private static FileUtil fileUtil;
    private static TaskManager taskManager;
    private static TaskExecutor taskExecutor;
    private static MemoryManager memoryManager;
    private static MemoryQuery memoryQuery;
    private static SkillManager skillManager;
    private static ConditionedReflex conditionedReflex;
    private static StateManager stateManager;
    private static BotSpawner botSpawner;
    private static BotController botController;
    private static CharacterManager characterManager;
    private static BehaviorObserver behaviorObserver;
    private static ExecutionLogger executionLogger;

    @Override
    public void onInitialize() {
        LOGGER.info("[AI Player] 初始化开始...");

        try {
            FileUtil.ensureDirectories();
            LOGGER.info("[AI Player] 目录结构已创建");
        } catch (Exception e) {
            LOGGER.error("[AI Player] 目录创建失败", e);
        }

        config = ModConfig.load();
        LOGGER.info("[AI Player] 配置已加载");

        botSpawner = new BotSpawner();
        stateManager = new StateManager();
        memoryManager = new MemoryManager(config);
        memoryQuery = new MemoryQuery(memoryManager);
        taskManager = new TaskManager();
        skillManager = new SkillManager();
        executionLogger = new ExecutionLogger();
        conditionedReflex = new ConditionedReflex(skillManager, config);
        taskExecutor = new TaskExecutor(taskManager, skillManager, stateManager, executionLogger);
        characterManager = new CharacterManager(config);
        behaviorObserver = new BehaviorObserver(characterManager, config);
        botController = new BotController(botSpawner, taskManager, taskExecutor, stateManager, conditionedReflex);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AICommand.register(dispatcher);
            LOGGER.info("[AI Player] 指令已注册");
        });

        ServerWorldEvents.LOAD.register((server, world) -> {
            LOGGER.info("[AI Player] 世界已加载");
        });

        ServerTickEvents.START_SERVER_TICK.register(server -> {
            if (botController != null) {
                botController.onTick(server);
            }
        });

        behaviorObserver.register();

        LOGGER.info("[AI Player] 初始化完成");
    }

    public static ModConfig getConfig() { return config; }
    public static TaskManager getTaskManager() { return taskManager; }
    public static MemoryManager getMemoryManager() { return memoryManager; }
    public static BotSpawner getBotSpawner() { return botSpawner; }
    public static CharacterManager getCharacterManager() { return characterManager; }
    public static SkillManager getSkillManager() { return skillManager; }
    public static StateManager getStateManager() { return stateManager; }
    public static ExecutionLogger getExecutionLogger() { return executionLogger; }
    public static BotController getBotController() { return botController; }
    public static ConditionedReflex getConditionedReflex() { return conditionedReflex; }
    public static MemoryQuery getMemoryQuery() { return memoryQuery; }
}
