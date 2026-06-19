package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.action.BlendedAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DomainRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger("e-agent");

    private final List<DomainExecutor<?, ?>> executors = new ArrayList<>();
    private final DomainSignal localBus = DomainSignal.NEUTRAL;
    private final ExecutorCPG.MotionCPG motionCPG = new ExecutorCPG.MotionCPG();
    private final ExecutorCPG.DigCPG digCPG = new ExecutorCPG.DigCPG();
    private final ExecutorCPG.CombatCPG combatCPG = new ExecutorCPG.CombatCPG();

    public void register(DomainExecutor<?, ?> executor) {
        if (executor == null) return;
        executors.add(executor);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <R> CompletableFuture<R> dispatch(DomainCommand cmd) {
        if (cmd == null) {
            return CompletableFuture.completedFuture(null);
        }
        for (var ex : executors) {
            if (ex.canHandle(cmd.commandType())) {
                DomainExecutor raw = ex;
                return (CompletableFuture<R>) raw.submit(cmd);
            }
        }
        String msg = "[DomainRouter] No executor registered for command type: " + cmd.commandType();
        LOGGER.error(msg);
        return CompletableFuture.failedFuture(new UnsupportedOperationException(msg));
    }

    public boolean executeBlended(BlendedAction action) {
        return executeBlended(action, "NORMAL");
    }

    public boolean executeBlended(BlendedAction action, String tier) {
        if (action == null || action == BlendedAction.NONE) return false;
        LOGGER.debug("[DomainRouter] executeBlended: {} (weight={}, tier={})", action.targetType(), action.weight(), tier);
        String type = commandTypeFromBlended(action.targetType());
        int priority = (int)(action.weight() * 10);
        double precision = com.izimi.eagent.brainstem.perception.AffordanceRouter.precisionForTier(tier);
        var cmd = new GenericCommand(type, action.targetType(), priority, action.direction(), precision);
        dispatch(cmd);
        return true;
    }

    private static String commandTypeFromBlended(String targetType) {
        if (targetType == null) return "idle";
        String lower = targetType.toLowerCase();
        if (lower.contains("flee") || lower.contains("evade")) return "movement";
        if (lower.contains("move") || lower.contains("approach")) return "movement";
        if (lower.contains("break") || lower.contains("dig") || lower.contains("mine")) return "break";
        if (lower.contains("combat") || lower.contains("attack")) return "combat";
        if (lower.contains("heal") || lower.contains("eat")) return "inventory";
        if (lower.contains("social") || lower.contains("interact")) return "inventory";
        return "idle";
    }

    public void tickAll() {
        motionCPG.tickPhase();
        digCPG.tickPhase();
        combatCPG.tickPhase();
        for (var ex : executors) {
            try {
                ex.tick();
            } catch (Exception e) {
                LOGGER.error("[DomainRouter] tick error in executor", e);
            }
        }
    }

    public DomainSignal getLocalBus() { return localBus; }
    public ExecutorCPG.MotionCPG getMotionCPG() { return motionCPG; }
    public ExecutorCPG.DigCPG getDigCPG() { return digCPG; }
    public ExecutorCPG.CombatCPG getCombatCPG() { return combatCPG; }

    public Map<String, FailureContext> getAllFailureContexts() {
        Map<String, FailureContext> map = new LinkedHashMap<>();
        for (var ex : executors) {
            var ctx = ex.getFailureContext();
            if (ctx != null) {
                map.put(ctx.commandType(), ctx);
            }
        }
        return map;
    }

    public FailureContext getFailureContext(String commandType) {
        for (var ex : executors) {
            if (ex.canHandle(commandType)) {
                var ctx = ex.getFailureContext();
                if (ctx != null) return ctx;
            }
        }
        return null;
    }
}
