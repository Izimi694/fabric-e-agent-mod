package com.izimi.eagent.api.impl;

import com.izimi.eagent.api.BotContext;
import com.izimi.eagent.api.CognitiveBrainAPI;
import com.izimi.eagent.api.WorldContext;
import com.izimi.eagent.brainstem.bot.BotManager;
import com.izimi.eagent.config.ModConfig;
import com.izimi.eagent.log.ExecutionLogger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class CognitiveBrain implements CognitiveBrainAPI {
    private final WorldContext worldContext;
    private final BotManager botManager;

    public CognitiveBrain(WorldContext worldContext, BotManager botManager) {
        this.worldContext = worldContext;
        this.botManager = botManager;
    }

    @Override
    public WorldContext world() { return worldContext; }

    @Override
    public BotContext bot(UUID botId) {
        var inst = botManager.getById(botId);
        return inst != null ? inst.getBotContext() : null;
    }

    @Override
    public BotContext bot(String name) {
        var inst = botManager.getByName(name);
        return inst != null ? inst.getBotContext() : null;
    }

    @Override
    public Collection<BotContext> allBots() {
        List<BotContext> contexts = new ArrayList<>();
        for (var inst : botManager.getAll()) {
            var ctx = inst.getBotContext();
            if (ctx != null) contexts.add(ctx);
        }
        return contexts;
    }

    @Override
    public int botCount() { return botManager.getAll().size(); }

    @Override
    public ModConfig config() { return worldContext.modConfig(); }

    @Override
    public ExecutionLogger executionLogger() { return worldContext.executionLogger(); }
}
