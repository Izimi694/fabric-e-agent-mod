package com.izimi.aiplayermod.bot;

import com.izimi.aiplayermod.skill.ConditionedReflex;
import com.izimi.aiplayermod.state.StateManager;
import com.izimi.aiplayermod.task.TaskExecutor;
import com.izimi.aiplayermod.task.TaskManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class BotController {
    private final BotSpawner botSpawner;
    private final TaskManager taskManager;
    private final TaskExecutor taskExecutor;
    private final StateManager stateManager;
    private final ConditionedReflex conditionedReflex;

    private int tickCounter = 0;
    private int stateSaveInterval = 200;

    public BotController(BotSpawner botSpawner, TaskManager taskManager,
                         TaskExecutor taskExecutor, StateManager stateManager,
                         ConditionedReflex conditionedReflex) {
        this.botSpawner = botSpawner;
        this.taskManager = taskManager;
        this.taskExecutor = taskExecutor;
        this.stateManager = stateManager;
        this.conditionedReflex = conditionedReflex;
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
}
