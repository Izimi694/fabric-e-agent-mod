package com.izimi.eagent.api.impl;

import com.izimi.eagent.api.AmygdalaAPI;
import com.izimi.eagent.api.BrainstemAPI;
import com.izimi.eagent.api.CortexAPI;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.amygdala.FamiliarityTracker;
import com.izimi.eagent.amygdala.SocialObserver;
import com.izimi.eagent.amygdala.character.BehaviorStats;
import com.izimi.eagent.brainstem.adapter.BasicActionAdapter;
import com.izimi.eagent.brainstem.bot.BotManager;
import com.izimi.eagent.brainstem.domain.DomainRouter;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import com.izimi.eagent.brainstem.scheduler.ILocalPlanner;
import com.izimi.eagent.brainstem.skill.SkillManager;
import com.izimi.eagent.config.ModConfig;
import com.izimi.eagent.cortex.api.AIChatHandler;
import com.izimi.eagent.cortex.api.AIClient;
import com.izimi.eagent.cortex.api.AITaskPlanner;
import com.izimi.eagent.cortex.api.TemplateManager;
import com.izimi.eagent.cortex.chat.LocalChatHandler;
import com.izimi.eagent.brainstem.scheduler.InhibitoryControl;
import com.izimi.eagent.cortex.planner.KnowledgeBase;
import com.izimi.eagent.log.ExecutionLogger;

public class WorldContextImpl implements WorldContext {

    private final InnateReflexRegistry innateReflexes;
    private final BasicActionAdapter basicActions;
    private final InhibitoryControl inhibitor;
    private DomainRouter domainRouter;
    private final SocialObserver socialObserver;
    private final FamiliarityTracker familiarityTracker;
    private final ILocalPlanner localPlanner;
    private final LocalChatHandler chatHandler;
    private final TemplateManager templateManager;
    private final KnowledgeBase knowledgeBase;
    private final AIClient aiClient;
    private final AIChatHandler chatAI;
    private final AITaskPlanner taskPlanner;
    private final SkillManager skillManager;
    private final BehaviorStats behaviorStats;
    private final ModConfig modConfig;
    private final ExecutionLogger executionLogger;
    private BotManager botManager;

    private final BrainstemAPI brainstemAPI;
    private final AmygdalaAPI amygdalaAPI;
    private final CortexAPI cortexAPI;

    public WorldContextImpl(
            InnateReflexRegistry innateReflexes,
            BasicActionAdapter basicActions,
            InhibitoryControl inhibitor,
            SocialObserver socialObserver,
            FamiliarityTracker familiarityTracker,
            ILocalPlanner localPlanner,
            LocalChatHandler chatHandler,
            TemplateManager templateManager,
            KnowledgeBase knowledgeBase,
            AIClient aiClient,
            AIChatHandler chatAI,
            AITaskPlanner taskPlanner,
            SkillManager skillManager,
            BehaviorStats behaviorStats,
            ModConfig modConfig,
            ExecutionLogger executionLogger
    ) {
        this.innateReflexes = innateReflexes;
        this.basicActions = basicActions;
        this.inhibitor = inhibitor;
        this.socialObserver = socialObserver;
        this.familiarityTracker = familiarityTracker;
        this.localPlanner = localPlanner;
        this.chatHandler = chatHandler;
        this.templateManager = templateManager;
        this.knowledgeBase = knowledgeBase;
        this.aiClient = aiClient;
        this.chatAI = chatAI;
        this.taskPlanner = taskPlanner;
        this.skillManager = skillManager;
        this.behaviorStats = behaviorStats;
        this.modConfig = modConfig;
        this.executionLogger = executionLogger;

        this.brainstemAPI = new BrainstemAPI() {
            @Override public InnateReflexRegistry innateReflexes() { return WorldContextImpl.this.innateReflexes; }
            @Override public BasicActionAdapter basicActions() { return WorldContextImpl.this.basicActions; }
            @Override public DomainRouter domainRouter() { return WorldContextImpl.this.domainRouter; }
            @Override public InhibitoryControl inhibitor() { return WorldContextImpl.this.inhibitor; }
        };

        this.amygdalaAPI = new AmygdalaAPI() {
            @Override public SocialObserver socialObserver() { return WorldContextImpl.this.socialObserver; }
            @Override public FamiliarityTracker familiarityTracker() { return WorldContextImpl.this.familiarityTracker; }
        };

        this.cortexAPI = new CortexAPI() {
            @Override public ILocalPlanner localPlanner() { return WorldContextImpl.this.localPlanner; }
            @Override public LocalChatHandler chatHandler() { return WorldContextImpl.this.chatHandler; }
            @Override public TemplateManager templateManager() { return WorldContextImpl.this.templateManager; }
            @Override public KnowledgeBase knowledgeBase() { return WorldContextImpl.this.knowledgeBase; }
            @Override public AIClient aiClient() { return WorldContextImpl.this.aiClient; }
            @Override public AIChatHandler chatAI() { return WorldContextImpl.this.chatAI; }
            @Override public AITaskPlanner taskPlanner() { return WorldContextImpl.this.taskPlanner; }
        };
    }

    @Override public BrainstemAPI brainstem() { return brainstemAPI; }
    @Override public AmygdalaAPI amygdala() { return amygdalaAPI; }
    @Override public CortexAPI cortex() { return cortexAPI; }
    @Override public SkillManager skillManager() { return skillManager; }
    @Override public BehaviorStats behaviorStats() { return behaviorStats; }
    @Override public ModConfig modConfig() { return modConfig; }
    @Override public ExecutionLogger executionLogger() { return executionLogger; }
    @Override public BotManager botManager() { return botManager; }

    public void setBotManager(BotManager botManager) { this.botManager = botManager; }

    public void setDomainRouter(DomainRouter router) { this.domainRouter = router; }
}
