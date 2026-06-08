package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.amygdala.BotParams;
import com.izimi.aiplayermod.amygdala.ConditionedReflex;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.OneShotAlarmSystem;
import com.izimi.aiplayermod.brainstem.HormonalSystem;
import com.izimi.aiplayermod.brainstem.IdleBrain;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;
import com.izimi.aiplayermod.cortex.chat.LocalChatHandler;
import com.izimi.aiplayermod.cortex.planner.PlanManager;
import com.izimi.aiplayermod.cortex.task.Task;
import com.izimi.aiplayermod.cortex.task.TaskManager;
import com.izimi.aiplayermod.hippocampus.MemoryManager;
import com.izimi.aiplayermod.state.StateManager;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;

public class MetaContext {

    private final UUID botId;
    private final String botName;
    private final BotParams params;
    private final HormonalSystem hormones;
    private final OneShotAlarmSystem alarms;
    private final ConditionedReflex conditionedReflex;
    private final DispatchReflex dispatchReflex;
    private final InnateReflexRegistry reflexRegistry;
    private final InhibitoryControl inhibitor;
    private final TaskManager taskManager;
    private final MemoryManager memoryManager;
    private final StateManager stateManager;
    private final IdleBrain idleBrain;
    private final ILocalPlanner localPlanner;
    private final LocalChatHandler localChatHandler;
    private final PlanManager planManager;
    private final ServerPlayerEntity bot;

    private String lastPlayerMessage = "";
    private int p3Cooldown = 0;

    public MetaContext(UUID botId, String botName, BotParams params, HormonalSystem hormones,
                       OneShotAlarmSystem alarms, ConditionedReflex conditionedReflex,
                       DispatchReflex dispatchReflex, InnateReflexRegistry reflexRegistry,
                       InhibitoryControl inhibitor, TaskManager taskManager,
                       MemoryManager memoryManager, StateManager stateManager,
                        IdleBrain idleBrain, ILocalPlanner localPlanner,
                        LocalChatHandler localChatHandler,
                        PlanManager planManager, ServerPlayerEntity bot) {
        this.botId = botId;
        this.botName = botName;
        this.params = params;
        this.hormones = hormones;
        this.alarms = alarms;
        this.conditionedReflex = conditionedReflex;
        this.dispatchReflex = dispatchReflex;
        this.reflexRegistry = reflexRegistry;
        this.inhibitor = inhibitor;
        this.taskManager = taskManager;
        this.memoryManager = memoryManager;
        this.stateManager = stateManager;
        this.idleBrain = idleBrain;
        this.localPlanner = localPlanner;
        this.localChatHandler = localChatHandler;
        this.planManager = planManager;
        this.bot = bot;
    }

    public UUID botId() { return botId; }
    public String botName() { return botName; }
    public BotParams params() { return params; }
    public HormonalSystem hormones() { return hormones; }
    public OneShotAlarmSystem alarms() { return alarms; }
    public ConditionedReflex conditionedReflex() { return conditionedReflex; }
    public DispatchReflex dispatchReflex() { return dispatchReflex; }
    public InnateReflexRegistry reflexRegistry() { return reflexRegistry; }
    public InhibitoryControl inhibitor() { return inhibitor; }
    public TaskManager taskManager() { return taskManager; }
    public MemoryManager memoryManager() { return memoryManager; }
    public StateManager stateManager() { return stateManager; }
    public IdleBrain idleBrain() { return idleBrain; }
    public ILocalPlanner localPlanner() { return localPlanner; }
    public LocalChatHandler localChatHandler() { return localChatHandler; }
    public PlanManager planManager() { return planManager; }
    public ServerPlayerEntity bot() { return bot; }

    public Task activeTask() {
        return taskManager != null ? taskManager.getActiveTask() : null;
    }

    public String lastPlayerMessage() { return lastPlayerMessage; }
    public void setLastPlayerMessage(String msg) { this.lastPlayerMessage = msg; }

    public int getP3Cooldown() { return p3Cooldown; }
    public void setP3Cooldown(int c) { this.p3Cooldown = c; }
    public void tickP3Cooldown() { if (p3Cooldown > 0) p3Cooldown--; }

    public boolean hasGroupActivity() { return false; }
    public boolean hasHighProficiencyReflex(ServerPlayerEntity bot) { return false; }
    public boolean hasRecentNovelty() { return false; }

    public double getLastProficiency() { return 0; }
    public boolean hasEnvironmentAnomaly() { return false; }
    public int getLastActionSuccessCount() { return 0; }
    public int getPlayerInactiveMinutes() { return 0; }
    public int getConsecutiveFailures() { return 0; }
    public boolean hasNovelEntity() { return false; }
    public boolean hasSuddenEnvironmentChange() { return false; }
    public boolean hasUrgentPlayerMessage() { return false; }

    public MetaContext withBot(ServerPlayerEntity newBot) {
        return new MetaContext(botId, botName, params, hormones, alarms,
                conditionedReflex, dispatchReflex, reflexRegistry, inhibitor,
                taskManager, memoryManager, stateManager, idleBrain, localPlanner, localChatHandler, planManager, newBot);
    }
}
