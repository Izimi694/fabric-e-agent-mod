package com.izimi.eagent.api;

import com.izimi.eagent.brainstem.scheduler.ILocalPlanner;
import com.izimi.eagent.cortex.api.AIChatHandler;
import com.izimi.eagent.cortex.api.AIClient;
import com.izimi.eagent.cortex.api.AITaskPlanner;
import com.izimi.eagent.cortex.api.TemplateManager;
import com.izimi.eagent.cortex.chat.LocalChatHandler;
import com.izimi.eagent.cortex.planner.KnowledgeBase;

public interface CortexAPI {
    ILocalPlanner localPlanner();
    LocalChatHandler chatHandler();
    TemplateManager templateManager();
    KnowledgeBase knowledgeBase();
    AIClient aiClient();
    AIChatHandler chatAI();
    AITaskPlanner taskPlanner();
}
