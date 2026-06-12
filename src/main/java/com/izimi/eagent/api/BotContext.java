package com.izimi.eagent.api;

import com.izimi.eagent.amygdala.BotParams;
import com.izimi.eagent.amygdala.ConditionedReflex;
import com.izimi.eagent.amygdala.DispatchReflex;
import com.izimi.eagent.amygdala.OneShotAlarmSystem;
import com.izimi.eagent.amygdala.learning.CorrelationDetector;
import com.izimi.eagent.amygdala.learning.LearningSystem;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.brainstem.IdleBrain;
import com.izimi.eagent.cortex.chat.ChatSessionManager;
import com.izimi.eagent.cortex.planner.PlanManager;
import com.izimi.eagent.cortex.task.TaskExecutor;
import com.izimi.eagent.cortex.task.TaskManager;
import com.izimi.eagent.hippocampus.MemoryManager;
import com.izimi.eagent.hormonal.HormonalSystem;
import com.izimi.eagent.state.StateManager;

import java.util.UUID;

public interface BotContext {
    UUID botId();
    String botName();
    WorldContext world();

    BotParams botParams();
    HormonalSystem hormonalSystem();
    ConditionedReflex conditionedReflex();
    OneShotAlarmSystem alarmSystem();
    BayesianModule bayesianModule();
    DispatchReflex dispatchReflex();

    TaskManager taskManager();
    TaskExecutor taskExecutor();
    StateManager stateManager();
    MemoryManager memoryManager();
    PlanManager planManager();

    IdleBrain idleBrain();
    CorrelationDetector correlationDetector();
    LearningSystem learningSystem();
    ChatSessionManager chatSessionManager();
}
