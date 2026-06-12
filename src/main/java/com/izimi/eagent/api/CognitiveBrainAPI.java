package com.izimi.eagent.api;

import com.izimi.eagent.config.ModConfig;
import com.izimi.eagent.log.ExecutionLogger;

import java.util.Collection;
import java.util.UUID;

public interface CognitiveBrainAPI {
    WorldContext world();
    BotContext bot(UUID botId);
    BotContext bot(String name);
    Collection<BotContext> allBots();
    int botCount();

    ModConfig config();
    ExecutionLogger executionLogger();
}
