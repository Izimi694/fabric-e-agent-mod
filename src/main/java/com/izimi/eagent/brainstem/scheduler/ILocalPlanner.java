package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.cortex.api.AIResponse;

public interface ILocalPlanner {
    AIResponse decompose(String message);
    boolean canHandle(String message);
}
