package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.api.BotContext;
import com.izimi.eagent.api.MetaState;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.amygdala.OneShotAlarmSystem;
import com.izimi.eagent.amygdala.learning.CorrelationDetector;
import com.izimi.eagent.brainstem.adapter.TemporalScaler;
import com.izimi.eagent.brainstem.innate.InnateReflex;
import com.izimi.eagent.brainstem.innate.InnateReflexRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public final class LowLevelDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private LowLevelDispatcher() {}

    // ── L0: Instinct ──

    public static boolean executeInstinctLayer(BotContext botCtx, WorldContext worldCtx,
                                                ServerPlayerEntity bot, TemporalScaler temporalScaler) {
        InnateReflexRegistry reg = worldCtx.brainstem().innateReflexes();
        InhibitoryControl inhibitor = worldCtx.brainstem().inhibitor();
        OneShotAlarmSystem alarms = botCtx.alarmSystem();

        if (reg != null) {
            InnateReflex safety = reg.highest(bot, 0);
            if (safety != null) {
                if (inhibitor != null && inhibitor.shouldVetoSafety(safety, bot,
                        null, worldCtx.behaviorStats())) {
                    LOGGER.debug("[LowLevelDispatcher] P0.5 veto safety: {}", safety.id());
                } else {
                    LOGGER.debug("[LowLevelDispatcher] L0 reflex: {} critical={}", safety.id(), safety.critical());
                    dispatchReflexAction(bot, safety, worldCtx, temporalScaler);
                    if (safety.critical()) return true;
                }
            }
        }

        if (alarms != null) {
            var threat = alarms.matchNearest(bot);
            if (threat != null && threat.type() == OneShotAlarmSystem.AlarmType.THREAT) {
                LOGGER.debug("[LowLevelDispatcher] Level2 threat: {}", threat.alarmId());
                dispatchReflexAction(bot,
                        new InnateReflex("alarm_" + threat.alarmId(), 0, false,
                                java.util.List.of(), new com.izimi.eagent.brainstem.innate.ReflexAction(threat.action(),
                                java.util.Map.of("speed", 0.3)), true), worldCtx, temporalScaler);
                return true;
            }
        }
        return false;
    }

    private static void dispatchReflexAction(ServerPlayerEntity bot, InnateReflex reflex,
                                              WorldContext worldCtx, TemporalScaler temporalScaler) {
        var adapter = worldCtx.brainstem().basicActions();
        if (adapter == null) return;
        float speedMul = temporalScaler != null ? temporalScaler.getSpeed() : 1.0f;
        switch (reflex.action().type()) {
            case "flee" -> adapter.flee(bot, reflex.action().getDouble("speed", 0.3) * speedMul);
            case "eat" -> adapter.eat(bot);
            case "retreat" -> adapter.retreat(bot, reflex.action().getDouble("speed", 0.25) * speedMul);
            case "avoidLava" -> adapter.avoidLava(bot, reflex.action().getDouble("speed", 0.2) * speedMul);
            case "seekShelter" -> adapter.seekShelter(bot, reflex.action().getDouble("speed", 0.1) * speedMul);
            case "collectItem" -> adapter.collectItem(bot, reflex.action().getDouble("speed", 0.15) * speedMul);
            case "sneak" -> adapter.sneak(bot, true);
        }
    }

    // ── L1: Habit ──

    public static boolean executeHabitLayer(BotContext botCtx, MetaState state,
                                            ServerPlayerEntity bot, CorrelationDetector correlationDetector) {
        var conditioned = botCtx.conditionedReflex();
        var taskManager = botCtx.taskManager();
        if (conditioned == null || taskManager == null) return false;

        var activeTask = taskManager.getActiveTask();
        if (activeTask != null && "running".equals(activeTask.getStatus())) {
            var reflexSkill = conditioned.match(activeTask);
            if (reflexSkill != null) {
                conditioned.executeReflex(reflexSkill, bot);
                return true;
            }
        }

        if (state.getP3Cooldown() <= 0) {
            var autoReflex = conditioned.scanAndTrigger(bot);
            if (autoReflex != null) {
                conditioned.executeReflex(autoReflex, bot);
                return true;
            }
        }

        if (correlationDetector != null) {
            return correlationDetector.tryExplore(bot);
        }
        return false;
    }

    // ── L2: Cortex Local ──

    public static boolean executeCortexLocal(BotContext botCtx, WorldContext worldCtx,
                                              MetaState state, ServerPlayerEntity bot) {
        String msg = state.consumePendingChat();
        if (msg == null) return false;

        var planner = worldCtx.cortex().localPlanner();

        if (planner != null && planner.canHandle(msg)) {
            var response = planner.decompose(msg);
            if (response != null && response.isAction()) {
                var planManager = botCtx.planManager();
                if (planManager != null) {
                    var plan = planManager.getActivePlan();
                    if (plan != null && !plan.subSteps.isEmpty()) {
                        botCtx.taskManager().createTaskFromPlan(msg, plan);
                        LOGGER.info("[LowLevelDispatcher] CortexLocal 从Plan创建任务: {} → {}步",
                                msg, plan.subSteps.size());
                        return true;
                    }
                }
                botCtx.taskManager().createTask(msg);
                return true;
            }
        }

        var chatHandler = worldCtx.cortex().chatHandler();
        if (chatHandler != null && chatHandler.canHandle(msg)) {
            UUID playerId = botCtx.botId();
            String response = chatHandler.getResponse(msg, botCtx.hormonalSystem(), playerId);
            if (response != null) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f" + response));
                LOGGER.info("[LowLevelDispatcher] CortexLocal 本地聊天: \"{}\" → \"{}\"",
                        msg, response);
                return true;
            }
        }

        return false;
    }

    // ── Idle ──

    public static boolean executeIdle(BotContext botCtx, ServerPlayerEntity bot,
                                       TemporalScaler temporalScaler) {
        var idleBrain = botCtx.idleBrain();
        if (idleBrain != null) {
            var suggestion = idleBrain.onTick();
            if (suggestion != null) {
                bot.sendMessage(Text.literal("§b[E-Agent] §f" + suggestion.text()));
                return true;
            }
        }

        animateIdle(bot, temporalScaler);
        return true;
    }

    private static void animateIdle(ServerPlayerEntity bot, TemporalScaler temporalScaler) {
        float speedMul = temporalScaler != null ? temporalScaler.getSpeed() : 1.0f;
        long tick = bot.age;
        if (tick % 40 < 20) {
            float yaw = bot.getYaw() + (float) (Math.sin(tick * 0.05) * 15);
            bot.setYaw(yaw);
            bot.setHeadYaw(yaw);
        }
        if (tick % Math.max(20, (int)(100 / speedMul)) == 0) {
            var pos = bot.getPos();
            double angle = Math.random() * Math.PI * 2;
            double dist = (2.0 + Math.random() * 3.0) * speedMul;
            var target = pos.add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            double dx = target.x - pos.x;
            double dz = target.z - pos.z;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                bot.setVelocity(new Vec3d(dx / len * 0.15 * speedMul, 0.08, dz / len * 0.15 * speedMul));
                bot.velocityModified = true;
            }
        }
    }
}
