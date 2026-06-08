package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.cortex.api.AIResponse;

public interface ILocalPlanner {
    AIResponse decompose(String message);
    boolean canHandle(String message);
}
