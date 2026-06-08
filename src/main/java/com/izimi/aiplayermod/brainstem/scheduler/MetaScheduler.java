package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.AIPlayerMod;
import com.izimi.aiplayermod.amygdala.DispatchReflex;
import com.izimi.aiplayermod.amygdala.OneShotAlarmSystem;
import com.izimi.aiplayermod.brainstem.HormonalSystem;
import com.izimi.aiplayermod.brainstem.innate.InnateReflex;
import com.izimi.aiplayermod.brainstem.innate.InnateReflexRegistry;
import com.izimi.aiplayermod.cortex.inhibitor.InhibitoryControl;
import com.izimi.aiplayermod.cortex.chat.LocalChatHandler;
import com.izimi.aiplayermod.cortex.planner.Plan;
import com.izimi.aiplayermod.cortex.task.Task;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MetaScheduler {

    private Perspective selectPerspective(MetaContext ctx) {
        HormonalSystem h = ctx.hormones();
        OneShotAlarmSystem alarms = ctx.alarms();
        ServerPlayerEntity bot = ctx.bot();

        if (alarms != null && alarms.hasThreatMatchNearby(bot))  return Perspective.SURVIVAL;
        if (h.getStress() > 0.7)                                   return Perspective.SURVIVAL;
        if (ctx.activeTask() != null)                              return Perspective.TASK;
        if (ctx.hasGroupActivity())                                return Perspective.SOCIAL;
        if (h.getCuriosity() > 0.6)                                return Perspective.CURIOUS;
        if (ctx.params().getBeta() > 0.02)                         return Perspective.CAUTIOUS;
        return Perspective.TASK;
    }

    private ProblemLabel labelProblem(MetaContext ctx, Perspective perspective) {
        InnateReflexRegistry reflex = ctx.reflexRegistry();
        OneShotAlarmSystem alarms = ctx.alarms();
        ServerPlayerEntity bot = ctx.bot();

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
                if (ctx.activeTask() != null)           return ProblemLabel.TASK_ACTIVE;
                if (ctx.hasHighProficiencyReflex(bot))  return ProblemLabel.ROUTINE;
                return ProblemLabel.TRIVIAL;
            }
            case SOCIAL -> {
                if (ctx.hasGroupActivity()) return ProblemLabel.SOCIAL;
                return ProblemLabel.TRIVIAL;
            }
            case CURIOUS -> {
                if (ctx.hasRecentNovelty()) return ProblemLabel.NOVEL;
                return ProblemLabel.FAMILIAR;
            }
            case CAUTIOUS -> {
                if (alarms != null && alarms.hasThreatMatchNearby(bot)) return ProblemLabel.LEARNED_THREAT;
                return ProblemLabel.ROUTINE;
            }
        }
        return ProblemLabel.TRIVIAL;
    }

    private FlowLevel getFlowAdjustment(MetaContext ctx) {
        if (ctx.getLastProficiency() >= 0.8 && !ctx.hasEnvironmentAnomaly())
            return FlowLevel.AUTOPILOT;
        if (ctx.getLastActionSuccessCount() > 10)
            return FlowLevel.AUTOPILOT;
        if (ctx.getPlayerInactiveMinutes() > 5)
            return FlowLevel.AUTOPILOT;
        if (ctx.getConsecutiveFailures() >= 2)
            return FlowLevel.OVERRIDE;
        if (ctx.hasNovelEntity())
            return FlowLevel.OVERRIDE;
        if (ctx.hasSuddenEnvironmentChange())
            return FlowLevel.OVERRIDE;
        if (ctx.hasUrgentPlayerMessage())
            return FlowLevel.OVERRIDE;
        return FlowLevel.NORMAL;
    }

    public void tick(MetaContext ctx, MinecraftServer server) {
        Perspective perspective = selectPerspective(ctx);
        ProblemLabel label = labelProblem(ctx, perspective);
        FlowLevel flow = getFlowAdjustment(ctx);

        DispatchReflex.DispatchAction action = null;
        if (ctx.dispatchReflex() != null) {
            action = ctx.dispatchReflex().match(label, flow);
        }

        if (action == null) {
            action = fallbackDispatch(label, flow);
        }

        boolean success = execute(action, ctx, server);

        if (ctx.dispatchReflex() != null) {
            ctx.dispatchReflex().recordOutcome(label, flow, action, success);
        }
    }

    private DispatchReflex.DispatchAction fallbackDispatch(ProblemLabel label, FlowLevel flow) {
        if (flow == FlowLevel.OVERRIDE) {
            return new DispatchReflex.DispatchAction("CORTEX_LOCAL", "override");
        }
        return switch (label) {
            case SURVIVAL, LEARNED_THREAT -> new DispatchReflex.DispatchAction("INSTINCT", label.name());
            case TASK_ACTIVE, ROUTINE -> new DispatchReflex.DispatchAction("HABIT", label.name());
            case FAMILIAR -> new DispatchReflex.DispatchAction("CORTEX_LOCAL", "familiar");
            case NOVEL -> new DispatchReflex.DispatchAction("CORTEX_LLM", "novel");
            case SOCIAL, TRIVIAL -> new DispatchReflex.DispatchAction("IDLE", label.name());
        };
    }

    private boolean execute(DispatchReflex.DispatchAction action, MetaContext ctx, MinecraftServer server) {
        ServerPlayerEntity bot = ctx.bot();
        if (bot == null) return false;

        switch (action.layer()) {
            case "INSTINCT" -> {
                return executeInstinctLayer(ctx, bot);
            }
            case "HABIT" -> {
                return executeHabitLayer(ctx, bot);
            }
            case "CORTEX_LOCAL" -> {
                return executeCortexLocal(ctx, bot);
            }
            case "CORTEX_LLM" -> {
                return executeCortexLLM(ctx, bot, server);
            }
            case "IDLE" -> {
                return executeIdle(ctx, bot);
            }
        }
        return false;
    }

    private boolean executeInstinctLayer(MetaContext ctx, ServerPlayerEntity bot) {
        InnateReflexRegistry reg = ctx.reflexRegistry();
        InhibitoryControl inhibitor = ctx.inhibitor();
        OneShotAlarmSystem alarms = ctx.alarms();

        if (reg != null) {
            InnateReflex safety = reg.highest(bot, 0);
            if (safety != null) {
                if (inhibitor != null && inhibitor.shouldVetoSafety(safety, bot,
                        null, AIPlayerMod.getBehaviorStats())) {
                    AIPlayerMod.LOGGER.debug("[MetaScheduler] P0.5 veto safety: {}", safety.id());
                } else {
                    dispatchReflexAction(bot, safety);
                    if (safety.critical()) return true;
                }
            }
        }

        if (alarms != null) {
            var threat = alarms.matchNearest(bot);
            if (threat != null && threat.type() == OneShotAlarmSystem.AlarmType.THREAT) {
                AIPlayerMod.LOGGER.debug("[MetaScheduler] Level2 threat: {}", threat.alarmId());
                dispatchReflexAction(bot,
                        new InnateReflex("alarm_" + threat.alarmId(), 0, false,
                                List.of(), new com.izimi.aiplayermod.brainstem.innate.ReflexAction(threat.action(),
                                java.util.Map.of("speed", 0.3)), true));
                return true;
            }
        }
        return false;
    }

    private void dispatchReflexAction(ServerPlayerEntity bot, InnateReflex reflex) {
        var adapter = AIPlayerMod.getActionAdapter();
        if (adapter == null) return;
        switch (reflex.action().type()) {
            case "flee" -> adapter.flee(bot, reflex.action().getDouble("speed", 0.3));
            case "eat" -> adapter.eat(bot);
            case "retreat" -> adapter.retreat(bot, reflex.action().getDouble("speed", 0.25));
            case "avoidLava" -> adapter.avoidLava(bot, reflex.action().getDouble("speed", 0.2));
            case "seekShelter" -> adapter.seekShelter(bot, reflex.action().getDouble("speed", 0.1));
            case "collectItem" -> adapter.collectItem(bot, reflex.action().getDouble("speed", 0.15));
        }
    }

    private boolean executeHabitLayer(MetaContext ctx, ServerPlayerEntity bot) {
        var conditioned = ctx.conditionedReflex();
        var taskManager = ctx.taskManager();
        if (conditioned == null || taskManager == null) return false;

        Task activeTask = taskManager.getActiveTask();
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            var reflexSkill = conditioned.match(activeTask);
            if (reflexSkill != null) {
                conditioned.executeReflex(reflexSkill, bot);
                return true;
            }
        }

        if (ctx.getP3Cooldown() <= 0) {
            var autoReflex = conditioned.scanAndTrigger(bot);
            if (autoReflex != null) {
                conditioned.executeReflex(autoReflex, bot);
                return true;
            }
        }
        return false;
    }

    private boolean executeCortexLocal(MetaContext ctx, ServerPlayerEntity bot) {
        if (!AIPlayerMod.hasPendingChat()) return false;

        String msg = AIPlayerMod.peekPendingChatMessage();
        if (msg == null) return false;

        ILocalPlanner planner = ctx.localPlanner();

        if (planner != null && planner.canHandle(msg)) {
            var pc = AIPlayerMod.consumePendingChat();
            if (pc != null) {
                var response = planner.decompose(pc.message());
                if (response != null && response.isAction()) {
                    var planManager = ctx.planManager();
                    if (planManager != null) {
                        var plan = planManager.getActivePlan();
                        if (plan != null && !plan.subSteps.isEmpty()) {
                            ctx.taskManager().createTaskFromPlan(pc.message(), plan);
                            AIPlayerMod.LOGGER.info("[MetaScheduler] CortexLocal 从Plan创建任务: {} → {}步",
                                    pc.message(), plan.subSteps.size());
                            return true;
                        }
                    }
                    ctx.taskManager().createTask(pc.message());
                    return true;
                }
            }
        }

        LocalChatHandler chatHandler = ctx.localChatHandler();
        if (chatHandler != null && chatHandler.canHandle(msg)) {
            var pc = AIPlayerMod.consumePendingChat();
            if (pc != null) {
                UUID playerId = ctx.botId();
                String response = chatHandler.getResponse(pc.message(), ctx.hormones(), playerId);
                if (response != null) {
                    bot.sendMessage(Text.literal("§b[AI_Assistant] §f" + response));
                    AIPlayerMod.LOGGER.info("[MetaScheduler] CortexLocal 本地聊天: \"{}\" → \"{}\"",
                            pc.message(), response);
                    return true;
                }
            }
        }

        return false;
    }

    private boolean executeCortexLLM(MetaContext ctx, ServerPlayerEntity bot, MinecraftServer server) {
        var aiClient = AIPlayerMod.getAIClient();
        if (aiClient == null || !aiClient.isConfigured()) return false;

        var pc = AIPlayerMod.consumePendingChat();
        if (pc == null) return false;

        var aiChatHandler = AIPlayerMod.getAiChatHandler();
        if (aiChatHandler == null) return false;

        aiChatHandler.handleChat(pc.message(), ctx.stateManager().loadState(),
                ctx.taskManager().getActiveTask(),
                ctx.memoryManager().getRecentMemories());
        return true;
    }

    private boolean executeIdle(MetaContext ctx, ServerPlayerEntity bot) {
        var idleBrain = ctx.idleBrain();
        if (idleBrain != null) {
            var suggestion = idleBrain.onTick();
            if (suggestion != null) {
                bot.sendMessage(net.minecraft.text.Text.literal("§b[AI_Assistant] §f" + suggestion.text()));
                return true;
            }
        }
        return false;
    }
}
