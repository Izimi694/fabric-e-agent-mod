package com.izimi.eagent.brainstem.bot;

import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.api.BotContext;
import com.izimi.eagent.api.MetaState;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.api.impl.BotContextImpl;
import com.izimi.eagent.amygdala.BotParams;
import com.izimi.eagent.amygdala.ConditionedReflex;
import com.izimi.eagent.amygdala.DispatchReflex;
import com.izimi.eagent.amygdala.OneShotAlarmSystem;
import com.izimi.eagent.amygdala.learning.CorrelationDetector;
import com.izimi.eagent.amygdala.learning.LearningSystem;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.cortex.api.PlaystylePack;
import com.izimi.eagent.hormonal.HormonalSystem;
import com.izimi.eagent.brainstem.IdleBrain;
import com.izimi.eagent.brainstem.adapter.TemporalScaler;
import com.izimi.eagent.brainstem.scheduler.MetaScheduler;
import com.izimi.eagent.brainstem.scheduler.MotivationEngine;
import com.izimi.eagent.brainstem.scheduler.Perspective;
import com.izimi.eagent.brainstem.scheduler.SurvivalChallengeMonitor;
import com.izimi.eagent.cortex.api.AITaskPlanner;
import com.izimi.eagent.cortex.chat.ChatSessionManager;
import com.izimi.eagent.cortex.planner.KnowledgeBase;
import com.izimi.eagent.cortex.planner.PlanManager;
import com.izimi.eagent.cortex.task.TaskExecutor;
import com.izimi.eagent.cortex.task.TaskManager;
import com.izimi.eagent.hippocampus.MemoryManager;
import com.izimi.eagent.state.StateManager;
import com.izimi.eagent.util.FileUtil;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BotInstance {
    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final UUID botId;
    private final String botName;
    private final BotPlayer botPlayer;

    private BotContext botContext;
    private WorldContext worldContext;

    // Per-bot components
    private final BotParams botParams;
    private final ConditionedReflex conditionedReflex;
    private final OneShotAlarmSystem alarms;
    private final HormonalSystem hormonalSystem;
    private final MotivationEngine motivationEngine;
    private final MetaScheduler metaScheduler;
    private final StateManager stateManager;
    private final TemporalScaler temporalScaler;
    private final CorrelationDetector correlationDetector;
    private final DispatchReflex dispatchReflex;
    private final BayesianModule bayesianModule;
    private final IdleBrain idleBrain;
    private final LearningSystem learningSystem;
    private final ReflexPackManager reflexPackManager;
    private final TaskManager taskManager;
    private final TaskExecutor taskExecutor;
    private final MemoryManager memoryManager;
    private final PlanManager planManager;

    private String nickname;
    private String pendingChatMessage;
    private int tickCounter = 0;
    private static final int STATE_SAVE_INTERVAL = 200;
    private boolean deathGenomeSaved = false;
    private boolean useLegacyScoring = false;

    public BotInstance(UUID botId, String botName, BotPlayer botPlayer) {
        this(botId, botName, botPlayer, null, null);
    }

    public BotInstance(UUID botId, String botName, BotPlayer botPlayer, BotParams inheritedParams) {
        this(botId, botName, botPlayer, inheritedParams, null);
    }

    public BotInstance(UUID botId, String botName, BotPlayer botPlayer, BotParams inheritedParams, WorldContext worldContext) {
        this.botId = botId;
        this.botName = botName;
        this.botPlayer = botPlayer;
        this.worldContext = worldContext;

        var config = worldContext.modConfig();
        var skillManager = worldContext.skillManager();
        var actionAdapter = worldContext.brainstem().basicActions();

        this.botParams = inheritedParams != null ? inheritedParams : BotParams.generate();
        this.botParams.withBotId(botId);
        this.conditionedReflex = new ConditionedReflex(skillManager, config, actionAdapter, botId);
        this.conditionedReflex.setBotInstance(this);
        this.alarms = new OneShotAlarmSystem(botId);
        this.hormonalSystem = new HormonalSystem();
        this.motivationEngine = new MotivationEngine();
        this.metaScheduler = new MetaScheduler(motivationEngine);
        this.stateManager = new StateManager(botId);
        this.temporalScaler = this.metaScheduler.getTemporalScaler();
        this.dispatchReflex = new DispatchReflex(botParams, botId);
        this.bayesianModule = new BayesianModule(botId);
        this.alarms.setBayesianModule(bayesianModule);
        this.conditionedReflex.setBayesianModule(bayesianModule);
        this.taskManager = new TaskManager(botId);
        this.taskExecutor = new TaskExecutor(taskManager, skillManager, worldContext.executionLogger());
        this.memoryManager = new MemoryManager(config, botId);
        this.correlationDetector = new CorrelationDetector(worldContext);
        this.correlationDetector.setBayesianModule(bayesianModule);
        this.learningSystem = new LearningSystem(conditionedReflex, skillManager);
        this.planManager = new PlanManager(new AITaskPlanner(worldContext.cortex().aiClient()), botId);
        this.planManager.setWorldContext(worldContext);

        this.idleBrain = new IdleBrain(taskManager, skillManager);
        this.reflexPackManager = new ReflexPackManager(botId, bayesianModule);
        this.reflexPackManager.setBotInstance(this);
        this.metaScheduler.setCorrelationDetector(correlationDetector);

        if (worldContext != null) {
            this.botContext = new BotContextImpl(
                    botId, botName, worldContext,
                    botParams, hormonalSystem,
                    conditionedReflex, alarms,
                    bayesianModule, dispatchReflex,
                    taskManager, taskExecutor,
                    stateManager, memoryManager,
                    planManager, idleBrain,
                    correlationDetector, learningSystem,
                    new ChatSessionManager(bayesianModule, botId)
            );
        }
    }

    public void tick(MinecraftServer server) {
        ServerPlayerEntity bot = botPlayer.asEntity();
        if (bot == null) return;

        if (bot.isRemoved()) {
            saveDeathGenome("removed");
            return;
        }

        if (bot.getHealth() <= 0 && !deathGenomeSaved) {
            SurvivalChallengeMonitor.recordDeath(botId);
            saveDeathGenome("killed");
        }

        // Auto-respawn: restore health so entity tick processes physics (travel/gravity/input)
        if (bot.getHealth() <= 0) {
            bot.setHealth(20.0f);
            bot.deathTime = 0;
            bot.fallDistance = 0;
            LOGGER.info("[BotInstance] 自动复活 {}", botName);
            // 传送到床/世界出生点 (替代原地复活)
            ServerWorld world = bot.getServerWorld();
            BlockPos spawnPos = world.getSpawnPos();
            float spawnAngle = world.getSpawnAngle();
            BlockPos bedPos = bot.getSpawnPointPosition();
            RegistryKey<World> bedDim = bot.getSpawnPointDimension();
            if (bedPos != null && bedDim != null && bedDim.equals(world.getRegistryKey())) {
                spawnPos = bedPos;
                spawnAngle = bot.getSpawnAngle();
            }
            bot.teleport(world, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, spawnAngle, 0.0f);
            bot.setVelocity(Vec3d.ZERO);
            LOGGER.info("[BotInstance] 复活传送到出生点: ({},{},{})", spawnPos.getX(), spawnPos.getY(), spawnPos.getZ());
        }

        // 每60tick状态摘要
        if (tickCounter > 0 && tickCounter % 60 == 0) {
            String reflex = conditionedReflex.getLastExecutedReflexId();
            double health = bot.getHealth();
            int food = bot.getHungerManager().getFoodLevel();
            String pos = String.format("(%.0f,%.0f,%.0f)", bot.getX(), bot.getY(), bot.getZ());
            LOGGER.info("[BotInstance] {} HP={}/{} 饿={} pos={} reflex={}",
                    botName, String.format("%.0f", health), String.format("%.0f", bot.getMaxHealth()),
                    food, pos, reflex != null ? reflex : "none");
        }

        tickCounter++;

        MetaState state = new MetaState();
        String pendingChat = consumePendingChat();
        if (pendingChat != null) state.setPendingChat(pendingChat);
        metaScheduler.tick(botContext, worldContext, bot, state, server);

        // Drive entity tick (physics/gravity/movement)
        botPlayer.tick();

        hormonalSystem.tick();

        // 每日快照 (每个游戏日 ~24000 ticks)
        if (tickCounter > 0 && tickCounter % 24000 == 0) {
            int day = tickCounter / 24000;
            var mgr = worldContext != null ? worldContext.botManager() : null;
            if (mgr != null) {
                SurvivalChallengeMonitor.printDailySnapshot(day, mgr.getAll());
            }
        }

        if (tickCounter % STATE_SAVE_INTERVAL == 0) {
            stateManager.saveState(bot);
        }
    }

    public void saveDeathGenome(String cause) {
        if (deathGenomeSaved) return;
        deathGenomeSaved = true;
        botParams.saveToPath(FileUtil.getBotParamsPath(botId));
        GenomeArchivist.saveGenome(botId, botParams, botName, cause);
    }

    public void sendMessage(String message) {
        if (botPlayer != null && !botPlayer.isRemoved()) {
            String display = (nickname != null && !nickname.isEmpty()) ? nickname : botName;
            botPlayer.asEntity().sendMessage(Text.literal("§b[" + display + "] §f" + message));
        }
    }

    // Per-bot component getters
    public BotContext getBotContext() { return botContext; }
    public UUID getBotId() { return botId; }
    public String getBotName() { return botName; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public BotPlayer getBotPlayer() { return botPlayer; }
    public ServerPlayerEntity asEntity() { return botPlayer != null ? botPlayer.asEntity() : null; }
    public BotParams getBotParams() { return botParams; }
    public ConditionedReflex getConditionedReflex() { return conditionedReflex; }
    public OneShotAlarmSystem getAlarms() { return alarms; }
    public HormonalSystem getHormonalSystem() { return hormonalSystem; }
    public MotivationEngine getMotivationEngine() { return motivationEngine; }
    public MetaScheduler getMetaScheduler() { return metaScheduler; }
    public StateManager getStateManager() { return stateManager; }
    public TemporalScaler getTemporalScaler() { return temporalScaler; }
    public CorrelationDetector getCorrelationDetector() { return correlationDetector; }
    public DispatchReflex getDispatchReflex() { return dispatchReflex; }
    public BayesianModule getBayesianModule() { return bayesianModule; }
    public IdleBrain getIdleBrain() { return idleBrain; }
    public LearningSystem getLearningSystem() { return learningSystem; }
    public TaskManager getTaskManager() { return taskManager; }
    public TaskExecutor getTaskExecutor() { return taskExecutor; }
    public MemoryManager getMemoryManager() { return memoryManager; }
    public PlanManager getPlanManager() { return planManager; }
    public ReflexPackManager getReflexPackManager() { return reflexPackManager; }

    /**
     * Apply a V2 PlaystylePack to this bot instance.
     * Chains: BotParams override → HormonalPreset → Perspective weights
     *         → KnowledgeBase playstyle switch → reflex pack import
     */
    public boolean applyPlaystylePack(PlaystylePack pack) {
        if (pack == null) return false;

        // 1. Profile: BotParams + HormonalPreset
        if (pack.profile() != null) {
            var p = pack.profile();
            botParams.override(p.alpha(), p.beta(), p.temperature());
            hormonalSystem.applyPreset(p.hormonalPreset());
            if (p.perspectiveWeights() != null && !p.perspectiveWeights().isEmpty()) {
                Map<Perspective, Double> weights = new java.util.HashMap<>();
                for (var e : p.perspectiveWeights().entrySet()) {
                    try {
                        weights.put(Perspective.valueOf(e.getKey().toUpperCase()), e.getValue());
                    } catch (IllegalArgumentException ignored) {}
                }
                motivationEngine.setPerspectiveWeights(weights);
            }
        }

        // 2. KnowledgeBase playstyle switch
        KnowledgeBase kb = worldContext != null ? worldContext.cortex().knowledgeBase() : null;
        if (kb != null && pack.knowledge() != null && !pack.knowledge().isEmpty()) {
            kb.setPlaystyleKnowledge(pack.packName(), pack.knowledge());
            kb.switchPlaystyle(pack.packName());
        }

        // 3. Reflexes (delegate to ReflexPackManager)
        if (pack.reflexes() != null && !pack.reflexes().isEmpty()) {
            try {
                java.nio.file.Path conditionedDir = FileUtil.getBotConditionedDir(botId);
                java.nio.file.Files.createDirectories(conditionedDir);
                int imported = 0;
                for (var entry : pack.reflexes().entrySet()) {
                    String reflexId = entry.getKey();
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = entry.getValue() instanceof Map
                        ? (Map<String, Object>) entry.getValue() : null;
                    if (data != null) {
                        java.nio.file.Path rf = conditionedDir.resolve(reflexId + ".json");
                        com.izimi.eagent.util.JsonUtil.writeToFileSafeAtomic(rf, data);
                        imported++;
                    }
                }
                LOGGER.info("[BotInstance] playstyle {}: 已导入 {} 个反射", pack.packName(), imported);
            } catch (java.io.IOException e) {
                LOGGER.warn("[BotInstance] playstyle {}: 反射导入失败: {}", pack.packName(), e.getMessage());
            }
        }

        // 4. Config overrides (shared pool constraints — logged for future runtime support)
        if (pack.config() != null && !pack.config().isEmpty()) {
            LOGGER.info("[BotInstance] playstyle {}: 配置覆盖 {} 项", pack.packName(), pack.config().size());
        }

        LOGGER.info("[BotInstance] playstyle {} 已应用: alpha={} beta={} temp={}",
                pack.packName(),
                String.format("%.3f", botParams.getAlpha()),
                String.format("%.4f", botParams.getBeta()),
                String.format("%.3f", botParams.getTemperature()));
        return true;
    }

    public void setPendingChat(String message) { this.pendingChatMessage = message; }
    public String consumePendingChat() {
        String msg = pendingChatMessage;
        pendingChatMessage = null;
        return msg;
    }

    public boolean isLegacyScoring() { return useLegacyScoring; }
    public void setLegacyScoring(boolean v) { this.useLegacyScoring = v; }

    public boolean isSpawned() {
        return botPlayer != null && !botPlayer.isRemoved();
    }
}
