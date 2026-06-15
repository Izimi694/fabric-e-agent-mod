package com.izimi.eagent.brainstem.scheduler;

import com.google.gson.JsonObject;
import com.izimi.eagent.api.BotContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.izimi.eagent.api.MetaState;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.amygdala.DispatchReflex;
import com.izimi.eagent.amygdala.OneShotAlarmSystem;
import com.izimi.eagent.amygdala.learning.CorrelationDetector;
import com.izimi.eagent.bayesian.BayesianModule;
import com.izimi.eagent.brainstem.adapter.TemporalScaler;
import com.izimi.eagent.brainstem.innate.InnateReflex;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import com.izimi.eagent.cortex.api.TemplateManager;
import com.izimi.eagent.cortex.api.TemplateMatcher;
import com.izimi.eagent.brainstem.navigation.LandmarkCalibrator;
import com.izimi.eagent.cortex.api.PersonaManager;
import com.izimi.eagent.cortex.chat.InputDigester;
import com.izimi.eagent.cortex.prefrontal.CognitiveControl;
import com.izimi.eagent.EAgent;
import com.izimi.eagent.hormonal.HormonalSystem;
import com.izimi.eagent.brainstem.scheduler.SurvivalChallengeMonitor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class MetaScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private static final int LLM_COOLDOWN_TICKS = 400;
    private static final int TIME_ESCALATION_TICKS = 200;
    private static final int FLOW_STUCK_THRESHOLD = 600;

    // ── e-based timing constants: 63.2% execution / 36.8% buffer ──
    private static final double EXECUTION_RATIO = 1.0 - (1.0 / Math.E); // ≈ 0.632
    private static final double PREEMPT_THRESHOLD = 1.0 / Math.E;

    // ── Dead-end detection ──
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    private static final double MIN_POSTERIOR_THRESHOLD = 0.1;

    // ── Rollback ──
    private static final int MAX_RETRY_COUNT = 3;
    private static final double MIN_ALT_THRESHOLD = 0.3;

    public record DeadEndResult(boolean isDeadEnd, String reason) {}
    public record RollbackStage(int stage, String action) {}

    private String lastExecutedNodeId = null;

    /** 计算可用时间片: 63.2% 执行, 36.8% 缓冲 */
    public static long computeTimeSlice(long totalLatencyBound, int taskCount, long switchOverheadMs) {
        if (taskCount <= 0) return totalLatencyBound;
        long available = totalLatencyBound - (switchOverheadMs * taskCount);
        if (available <= 0) return Math.max(1, totalLatencyBound / taskCount);
        return (long) (available / taskCount * EXECUTION_RATIO);
    }

    /** 当新任务优先级超出当前任务优先级 × (1 + 1/e) 时触发抢占 */
    public static boolean shouldPreempt(double currentPriority, double newPriority) {
        return newPriority > currentPriority * (1.0 + PREEMPT_THRESHOLD);
    }

    private final MotivationEngine motivationEngine;
    private final UrgencyClassifier urgencyClassifier;
    private final TemporalScaler temporalScaler;
    private final TemplateMatcher templateMatcher;
    private CorrelationDetector correlationDetector;
    private CognitiveControl cognitiveControl;
    private ReflectionCycle reflectionCycle;
    private LandmarkCalibrator landmarkCalibrator;
    private final InputDigester inputDigester = new InputDigester();

    public MetaScheduler(MotivationEngine motivationEngine) {
        this.motivationEngine = motivationEngine;
        this.urgencyClassifier = new UrgencyClassifier();
        this.temporalScaler = new TemporalScaler(this.urgencyClassifier);
        this.templateMatcher = new TemplateMatcher();
    }

    public void setCorrelationDetector(CorrelationDetector cd) {
        this.correlationDetector = cd;
    }

    public void setCognitiveControl(CognitiveControl cc) {
        this.cognitiveControl = cc;
    }

    public void setReflectionCycle(ReflectionCycle rc) {
        this.reflectionCycle = rc;
    }

    public void setLandmarkCalibrator(LandmarkCalibrator lc) {
        this.landmarkCalibrator = lc;
    }

    private ProblemLabel labelProblem(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot, Perspective perspective) {
        InnateReflexRegistry reflex = worldCtx.brainstem().innateReflexes();
        OneShotAlarmSystem alarms = botCtx.alarmSystem();

        switch (perspective) {
            case SURVIVAL -> {
                if (reflex != null) {
                    InnateReflex critical = reflex.highest(bot, 0);
                    if (critical != null && critical.critical()) return ProblemLabel.SURVIVAL;
                }
                if (alarms != null && alarms.hasThreatMatchNearby(bot)) return ProblemLabel.LEARNED_THREAT;
                if (reflex != null && reflex.highest(bot, 0) != null) return ProblemLabel.SURVIVAL;
                return ProblemLabel.TRIVIAL;
            }
            case TASK -> {
                if (botCtx.taskManager() != null && botCtx.taskManager().getActiveTask() != null)
                    return ProblemLabel.TASK_ACTIVE;
                if (botCtx.conditionedReflex() != null && botCtx.conditionedReflex().getHighestProficiency() >= 0.8)
                    return ProblemLabel.ROUTINE;
                return ProblemLabel.TRIVIAL;
            }
            case SOCIAL -> {
                var social = worldCtx.amygdala().socialObserver();
                if (social != null && social.getNearbyPlayerCount() > 1) return ProblemLabel.SOCIAL;
                return ProblemLabel.TRIVIAL;
            }
            case CURIOUS -> {
                var h = botCtx.hormonalSystem();
                if (h.getCuriosity() > h.getCuriosityThreshold(botCtx.botParams().getBeta(), h.getStress()))
                    return ProblemLabel.NOVEL;
                return ProblemLabel.FAMILIAR;
            }
            case CAUTIOUS -> {
                if (alarms != null && alarms.hasThreatMatchNearby(bot)) return ProblemLabel.LEARNED_THREAT;
                return ProblemLabel.ROUTINE;
            }
        }
        return ProblemLabel.TRIVIAL;
    }

    private FlowLevel getFlowAdjustment(BotContext botCtx, ServerPlayerEntity bot, MetaState state) {
        double proficiency = botCtx.conditionedReflex() != null ? botCtx.conditionedReflex().getHighestProficiency() : 0;
        if (proficiency >= 0.8 && !state.hasSuddenEnvironmentChange())
            return FlowLevel.AUTOPILOT;
        if (state.getLastActionSuccessCount() > 10)
            return FlowLevel.AUTOPILOT;
        if (state.getPlayerInactiveMinutes() > 5)
            return FlowLevel.AUTOPILOT;
        if (botCtx.conditionedReflex() != null && botCtx.conditionedReflex().getConsecutiveFailures() >= 2)
            return FlowLevel.OVERRIDE;
        if (state.hasNovelEntity())
            return FlowLevel.OVERRIDE;
        if (state.hasSuddenEnvironmentChange())
            return FlowLevel.OVERRIDE;
        if (state.hasUrgentPlayerMessage())
            return FlowLevel.OVERRIDE;

        if (state.getTicksInCurrentLabel() > FLOW_STUCK_THRESHOLD)
            return FlowLevel.OVERRIDE;

        if (state.getTicksInCurrentLabel() > TIME_ESCALATION_TICKS) {
            double urgency = urgencyClassifier.computeUrgency(
                    botCtx.hormonalSystem(), bot, state.getTicksInCurrentLabel());
            if (urgency > 0.5) return FlowLevel.OVERRIDE;
        }

        return FlowLevel.NORMAL;
    }

    public void tick(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot, MetaState state, MinecraftServer server) {
        state.incrementTickSinceLastLLM();
        state.tickNovelEntities();

        // 检查模板结果 (异步 LLM)
        if (state.hasPendingTemplateResult()) {
            TemplateManager.TemplateType pendingType = state.getPendingTemplateType();
            String pendingMsg = state.getPendingTemplateMessage();
            CompletableFuture<JsonObject> future = state.consumePendingTemplateResult();
            if (future != null && future.isDone()) {
                processTemplateResult(future, botCtx, worldCtx, bot, state, pendingType, pendingMsg);
            } else if (future != null) {
                state.setPendingTemplateResult(future, pendingType, pendingMsg);
            }
        }

        DriveState drives = motivationEngine.computeDrives(botCtx, worldCtx, bot);
        Perspective perspective = motivationEngine.select(botCtx, drives);

        ProblemLabel label = labelProblem(botCtx, worldCtx, bot, perspective);
        state.setCurrentProblemLabel(label);

        FlowLevel flow = getFlowAdjustment(botCtx, bot, state);

        temporalScaler.update(botCtx.hormonalSystem(), bot, state.getTicksInCurrentLabel());

        DispatchReflex.DispatchAction action = null;
        if (botCtx.dispatchReflex() != null) {
            action = botCtx.dispatchReflex().match(label, flow);
        }

        if (action == null) {
            action = fallbackDispatch(label, flow);
        }

        if (isLLMAction(action) && !shouldInvokeLLM(worldCtx, state, label, flow)) {
            LOGGER.debug("[MetaScheduler] LLM gate denied: {} {}, falling back to HABIT", label, flow);
            action = new DispatchReflex.DispatchAction("HABIT", "llm_gate");
        }

        boolean success = execute(action, botCtx, worldCtx, bot, state, server, perspective);

        if (botCtx.dispatchReflex() != null) {
            if ("llm_gate".equals(action.reason())) {
                botCtx.dispatchReflex().recordGateEvent(success);
            }
            botCtx.dispatchReflex().recordOutcome(label, flow, action, success);
        }

        botCtx.hormonalSystem().tick();
    }

    // ── HABIT layer execution with full gating ──

    private boolean executeHabitLayerWithGating(BotContext botCtx, MetaState state, ServerPlayerEntity bot, Perspective perspective) {
        var conditioned = botCtx.conditionedReflex();
        var taskManager = botCtx.taskManager();
        if (conditioned == null || taskManager == null) return false;

        var activeTask = taskManager.getActiveTask();
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            var reflexSkill = conditioned.match(activeTask);
            if (reflexSkill != null) {
                LOGGER.info("[MetaScheduler] HABIT execute reflex: {}", reflexSkill.getSkillId());
                if (tryExecuteReflex(botCtx, bot, reflexSkill.getSkillId(), reflexSkill)) return true;
            }
            var taskExecutor = botCtx.taskExecutor();
            if (taskExecutor != null) {
                LOGGER.info("[MetaScheduler] HABIT execute task: {}", activeTask.getGoal());
                taskExecutor.executeTask(bot, activeTask);
                return true;
            }
        }

        if (state.getP3Cooldown() <= 0) {
            var autoReflex = conditioned.scanAndTrigger(bot, perspective);
            if (autoReflex != null) {
                LOGGER.info("[MetaScheduler] HABIT scanAndTrigger: {} (auto)", autoReflex.getSkillId());
                if (tryExecuteReflex(botCtx, bot, autoReflex.getSkillId(), autoReflex)) return true;
            }
        }

        if (correlationDetector != null) {
            return correlationDetector.tryExplore(bot);
        }
        return false;
    }

    private boolean tryExecuteReflex(BotContext botCtx, ServerPlayerEntity bot,
                                      String reflexId, com.izimi.eagent.brainstem.skill.Skill skill) {
        var conditioned = botCtx.conditionedReflex();
        var bayesian = botCtx.bayesianModule();
        var h = botCtx.hormonalSystem();

        if (bayesian != null) {
            double posterior = bayesian.predictSuccess(reflexId, conditioned.extractContextFeatures(bot));
            if (posterior < 0.05) {
                LOGGER.debug("[MetaScheduler] Gate-Bayesian: veto {} (posterior={})", reflexId, posterior);
                return false;
            }
        }

        var precond = conditioned.checkPreconditions(reflexId, bot, h);
        if (!precond.passed()) {
            LOGGER.debug("[MetaScheduler] Gate-Precondition: veto {} → {}", reflexId, precond.reason());
            return false;
        }

        if (cognitiveControl != null && h != null) {
            String veto = cognitiveControl.checkReflex(reflexId, h.getNeuroState());
            if (veto != null) {
                LOGGER.debug("[MetaScheduler] Gate-CognitiveControl: veto {} → {}", reflexId, veto);
                return false;
            }
        }

        conditioned.executeReflex(skill, bot);
        lastExecutedNodeId = reflexId;

        DeadEndResult deadEnd = isDeadEnd(botCtx, null, bot, reflexId);
        if (deadEnd.isDeadEnd()) {
            LOGGER.warn("[MetaScheduler] Dead-end: {} → {}", reflexId, deadEnd.reason());
            if (reflectionCycle != null && conditioned != null) {
                ReflectionCycle.Result rcResult = reflectionCycle.evaluate(
                        conditioned, bayesian, landmarkCalibrator, bot);
                if (rcResult == ReflectionCycle.Result.RESOLVED
                        || rcResult == ReflectionCycle.Result.CALIBRATED) {
                    LOGGER.info("[MetaScheduler] 反思周期解决死路: {} ({})", reflexId, rcResult);
                    return true;
                }
            }
            RollbackStage rb = rollback(botCtx, null, bot, reflexId, 0, 0);
            LOGGER.info("[MetaScheduler] Rollback: stage={} action={}", rb.stage(), rb.action());
        }

        return true;
    }

    private boolean isLLMAction(DispatchReflex.DispatchAction action) {
        return action != null && "CORTEX_LLM".equals(action.layer());
    }

    private boolean shouldInvokeLLM(WorldContext worldCtx, MetaState state, ProblemLabel label, FlowLevel flow) {
        var aiClient = worldCtx.cortex().aiClient();
        if (aiClient == null || !aiClient.isConfigured()) return false;
        if (state.getTickSinceLastLLM() < LLM_COOLDOWN_TICKS) return false;
        if (state.hasRecentLLMFailure()) return false;
        if (label != ProblemLabel.NOVEL && flow != FlowLevel.OVERRIDE) return false;

        var lp = worldCtx.cortex().localPlanner();
        if (lp != null && lp.canHandle(state.getLastPlayerMessage())) return false;

        return true;
    }

    private DispatchReflex.DispatchAction fallbackDispatch(ProblemLabel label, FlowLevel flow) {
        if (flow == FlowLevel.OVERRIDE) {
            return new DispatchReflex.DispatchAction("CORTEX_LOCAL", "override");
        }
        return switch (label) {
            case SURVIVAL, LEARNED_THREAT -> new DispatchReflex.DispatchAction("INSTINCT", label.name());
            case TASK_ACTIVE, ROUTINE -> new DispatchReflex.DispatchAction("HABIT", label.name());
            case FAMILIAR -> new DispatchReflex.DispatchAction("CORTEX_LOCAL", "familiar");
            case NOVEL -> new DispatchReflex.DispatchAction("HABIT", "novel_fallback");
            case SOCIAL, TRIVIAL -> new DispatchReflex.DispatchAction("IDLE", label.name());
        };
    }

    private boolean execute(DispatchReflex.DispatchAction action, BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot, MetaState state, MinecraftServer server, Perspective perspective) {
        if (bot == null) return false;

        LOGGER.debug("[MetaScheduler] Dispatch: {} ({})", action.layer(), action.reason());

        return switch (action.layer()) {
            case "INSTINCT" -> LowLevelDispatcher.executeInstinctLayer(botCtx, worldCtx, bot, temporalScaler);
            case "HABIT" -> executeHabitLayerWithGating(botCtx, state, bot, perspective);
            case "CORTEX_LOCAL" -> LowLevelDispatcher.executeCortexLocal(botCtx, worldCtx, state, bot);
            case "CORTEX_LLM" -> executeCortexLLM(botCtx, worldCtx, state, bot);
            case "IDLE" -> LowLevelDispatcher.executeIdle(botCtx, bot, temporalScaler);
            default -> false;
        };
    }


    private boolean executeCortexLLM(BotContext botCtx, WorldContext worldCtx, MetaState state, ServerPlayerEntity bot) {
        var templateManager = worldCtx.cortex().templateManager();
        if (templateManager == null) return false;

        String msg = state.consumePendingChat();
        if (msg == null) return false;
        state.setLastPlayerMessage(msg);

        // TemplateMatcher 路由
        TemplateManager.TemplateType templateType = templateMatcher.match(msg, botCtx, worldCtx);
        if (templateType == null) {
            // 已由 LocalChatHandler 或反射处理 — 返回成功
            LOGGER.debug("[MetaScheduler] 本地处理: {}", msg);
            return true;
        }

        // CHAT_RESPONSE 预算检查
        if (templateType == TemplateManager.TemplateType.CHAT_RESPONSE && state.isChatBudgetExhausted()) {
            // 预算耗尽 → 回退 LocalChatHandler
            var localChat = worldCtx.cortex().chatHandler();
            if (localChat != null && bot != null) {
                String fallback = localChat.getResponse(msg, botCtx.hormonalSystem(), botCtx.botId());
                if (fallback != null) {
                    bot.sendMessage(Text.literal("§b[E-Agent] §f" + fallback));
                }
            }
            return true;
        }

        // CLARIFICATION 调用限制: 同一对话轮次只调用一次
        if (templateType == TemplateManager.TemplateType.CLARIFICATION && state.hasRecentLLMFailure()) {
            // 用户二次模糊 → 预设回复
            if (bot != null) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f请更具体地描述你的需求。"));
            }
            return true;
        }

        // 构建模板上下文
        Map<String, Object> context = buildTemplateContext(templateType, msg, botCtx, worldCtx);

        state.resetTickSinceLastLLM();
        try {
            LOGGER.info("[LLM] L6 dispatch: template={}, msg={}", templateType, msg.length() > 60 ? msg.substring(0, 60) + "..." : msg);
            SurvivalChallengeMonitor.recordLLMCall(botCtx.botId());
            CompletableFuture<JsonObject> future = templateManager.fill(templateType, context);
            // 如果被限速返回 null, 直接回退
            if (future == null) {
                LOGGER.warn("[MetaScheduler] 模板被限速 {}", templateType);
                return false;
            }
            state.setPendingTemplateResult(future, templateType, msg);
            state.setRecentLLMFailure(false);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[MetaScheduler] LLM调用失败: {}", e.getMessage());
            state.setRecentLLMFailure(true);
            return false;
        }
    }

    private Map<String, Object> buildTemplateContext(TemplateManager.TemplateType type, String msg,
                                                     BotContext botCtx, WorldContext worldCtx) {
        Map<String, Object> ctx = new java.util.HashMap<>();
        var hormonal = botCtx.hormonalSystem();
        switch (type) {
            case CLARIFICATION -> {
                var d = inputDigester.digest(msg);
                ctx.put("userInput", d.rawPreview());
                ctx.put("intent", d.intent());
                ctx.put("entities", d.entities());
                ctx.put("count", d.count());
                ctx.put("availableTemplates", "TASK_PLAN, REFLEX_CREATE, CHAT_RESPONSE");
            }
            case TASK_PLAN -> {
                ctx.put("goal", msg);
                ctx.put("availableActions", "moveTo, dig, attack, placeBlock, useItem, equipItem, craft, chat, jump, lookAt, openBlock, closeWindow, clickSlot");
            }
            case REFLEX_CREATE -> {
                String[] parts = msg.split(" ");
                ctx.put("skill", parts.length > 1 ? parts[0] : "action");
                ctx.put("target", parts.length > 1 ? parts[1] : msg);
            }
            case CHAT_RESPONSE -> {
                var d = inputDigester.digest(msg);
                ctx.put("playerMessage", d.rawPreview());
                ctx.put("intent", d.intent());
                ctx.put("entities", d.entities());
                ctx.put("count", d.count());
                String mood = hormonal != null ? deriveMoodSummary(hormonal) : "平静";
                ctx.put("contextInfo", mood);
                PersonaManager pm = EAgent.getPersonaManager();
                String hint = pm != null ? pm.getFormatHint() : "";
                if (!hint.isEmpty()) ctx.put("formatHint", hint);
            }
        }
        return ctx;
    }

    /** 4D 状态向量 → 简短情绪摘要，比原始数值更省 token */
    private static String deriveMoodSummary(com.izimi.eagent.hormonal.HormonalSystem h) {
        double ne = h.getNE();
        double da = h.getDA();
        double st = h.getSerotonin();
        double cu = h.getCuriosity();
        StringBuilder b = new StringBuilder();
        if (ne > 0.5) b.append("警觉中");
        if (da > 0.6) b.append("、状态好");
        else if (da < 0.3) b.append("、低迷");
        if (st > 0.6) b.append("、谨慎");
        if (cu > 0.6) b.append("、好奇");
        if (b.isEmpty()) b.append("平静");
        return b.toString();
    }

    private void processTemplateResult(CompletableFuture<JsonObject> future, BotContext botCtx,
                                       WorldContext worldCtx, ServerPlayerEntity bot, MetaState state,
                                       TemplateManager.TemplateType type, String originalMsg) {
        JsonObject result;
        try {
            result = future.get();
        } catch (Exception e) {
            LOGGER.warn("[MetaScheduler] 模板结果获取失败: {}", e.getMessage());
            return;
        }
        if (result == null) return;

        switch (type) {
            case CLARIFICATION -> {
                boolean ambiguous = result.get("ambiguity_detected") != null && result.get("ambiguity_detected").getAsBoolean();
                if (ambiguous && bot != null) {
                    String question = result.has("suggested_question") ? result.get("suggested_question").getAsString() : "请更具体地描述你的需求。";
                    bot.sendMessage(Text.literal("§b[E-Agent] §f" + question));
                }
            }
            case CHAT_RESPONSE -> {
                if (bot != null) {
                    String reply = result.has("reply_text") ? result.get("reply_text").getAsString() : "";
                    if (!reply.isEmpty()) {
                        bot.sendMessage(Text.literal("§b[E-Agent] §f" + reply));
                    }
                    state.consumeChatBudget();
                }
            }
            case TASK_PLAN -> {
                // 将 LLM 填的 DAG 固化为 TaskDAG + ReflexChain
                String taskId = result.has("task_id") ? result.get("task_id").getAsString() : "task_" + System.currentTimeMillis();
                LOGGER.info("[MetaScheduler] 收到任务DAG: {} ({} subtasks)", taskId,
                    result.has("subtasks") ? result.getAsJsonArray("subtasks").size() : 0);
                // TaskDAG.fromLLMJson() 将被调用来解析;
                // 目前集成点在 TaskManager, 这里先创建普通任务
                var taskManager = botCtx.taskManager();
                if (taskManager != null) {
                    taskManager.createTask(originalMsg != null ? originalMsg : taskId);
                }
            }
            case REFLEX_CREATE -> {
                String reflexId = result.has("reflex_id") ? result.get("reflex_id").getAsString() : "";
                LOGGER.info("[MetaScheduler] 收到新反射: {}", reflexId);
                // 反射固化通过 ConditionedReflex.solidifySequence() 完成,
                // 这里先记录日志, 具体固化由填坑 hook 处理
            }
        }
    }



    // ── Dead-end Detection ──

    public DeadEndResult isDeadEnd(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot, String reflexId) {
        var conditioned = botCtx.conditionedReflex();
        if (conditioned == null) return new DeadEndResult(false, null);

        if (conditioned.getConsecutiveFailures() >= MAX_CONSECUTIVE_FAILURES) {
            return new DeadEndResult(true, "CONSECUTIVE_FAILURES");
        }

        var bayesian = botCtx != null ? botCtx.bayesianModule() : null;
        if (bayesian != null) {
            double posterior = bayesian.predictSuccess(reflexId, conditioned.extractContextFeatures(bot));
            if (posterior < MIN_POSTERIOR_THRESHOLD) {
                return new DeadEndResult(true, "LOW_POSTERIOR");
            }
        }

        double confidence = conditioned.getConfidence(reflexId);
        if (conditioned.getConsecutiveFailures() > 0 && confidence < 0.1) {
            return new DeadEndResult(true, "EXPLORATION_EXHAUSTED");
        }

        return new DeadEndResult(false, null);
    }

    // ── Rollback 5-Stage ──

    public RollbackStage rollback(BotContext botCtx, WorldContext worldCtx, ServerPlayerEntity bot,
                                  String nodeId, int retryCount, int stage) {
        switch (stage) {
            case 0: { // Stage 1: 本地重试
                if (retryCount < MAX_RETRY_COUNT) {
                    LOGGER.debug("[MetaScheduler] Rollback Stage1: 本地重试 {} (retry={})", nodeId, retryCount);
                    return new RollbackStage(1, "retry");
                }
                return rollback(botCtx, worldCtx, bot, nodeId, retryCount, 1);
            }
            case 1: { // Stage 2: 替代方案
                LOGGER.debug("[MetaScheduler] Rollback Stage2: 寻找替代 {}", nodeId);
                var bayesian = botCtx != null ? botCtx.bayesianModule() : null;
                if (bayesian != null) {
                    var alternatives = bayesian.inferForward(
                            new BayesianModule.BotState(null, null, null, null),
                            List.of());
                    if (!alternatives.isEmpty()) {
                        String altId = alternatives.get(0).getKey();
                        double altScore = alternatives.get(0).getValue();
                        if (altScore > MIN_ALT_THRESHOLD) {
                            LOGGER.debug("[MetaScheduler] Rollback Stage2: 替代方案 {} (score={})", altId, altScore);
                            return new RollbackStage(2, "alternative:" + altId);
                        }
                    }
                }
                return rollback(botCtx, worldCtx, bot, nodeId, retryCount, 2);
            }
            case 2: { // Stage 3: 回溯上游
                LOGGER.debug("[MetaScheduler] Rollback Stage3: 回溯上游 {}", nodeId);
            var bayesian = botCtx != null ? botCtx.bayesianModule() : null;
                if (bayesian != null && lastExecutedNodeId != null) {
                    String upId = lastExecutedNodeId;
                    double upConfidence = bayesian.getConfidence(upId);
                    if (upConfidence < 0.3) {
                        LOGGER.debug("[MetaScheduler] Rollback Stage3: 回溯到 {} (conf={})", upId, upConfidence);
                        return new RollbackStage(3, "backtrack:" + upId);
                    }
                }
                return rollback(botCtx, worldCtx, bot, nodeId, retryCount, 3);
            }
            case 3: { // Stage 4: 麦穗探索
                LOGGER.debug("[MetaScheduler] Rollback Stage4: 麦穗探索 {}", nodeId);
                HormonalSystem h = botCtx.hormonalSystem();
                double exploreProb = MotivationEngine.wheatEarExplore(0.3, h);
                if (exploreProb > Math.random()) {
                    return new RollbackStage(4, "explore");
                }
                return rollback(botCtx, worldCtx, bot, nodeId, retryCount, 4);
            }
            case 4: // Stage 5: LLM 重新规划
            default:
                LOGGER.warn("[MetaScheduler] Rollback Stage5: LLM重新规划 {}", nodeId);
                return new RollbackStage(5, "llm_replan");
        }
    }

    public TemporalScaler getTemporalScaler() { return temporalScaler; }
}
