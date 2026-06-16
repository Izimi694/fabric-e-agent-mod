package com.izimi.eagent.brainstem.domain;

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

    public void tickAll() {
        for (var ex : executors) {
            try {
                ex.tick();
            } catch (Exception e) {
                LOGGER.error("[DomainRouter] tick error in executor", e);
            }
        }
    }

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
