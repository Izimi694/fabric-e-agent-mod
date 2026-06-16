package com.izimi.eagent.brainstem.domain;

import java.util.concurrent.CompletableFuture;

public interface DomainExecutor<C extends DomainCommand, R> {
    boolean canHandle(String commandType);
    CompletableFuture<R> submit(C command);
    void tick();
    FailureContext getFailureContext();
}
