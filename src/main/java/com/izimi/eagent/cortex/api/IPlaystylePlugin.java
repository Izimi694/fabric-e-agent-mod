package com.izimi.eagent.cortex.api;

import com.izimi.eagent.api.BotContext;
import com.izimi.eagent.api.WorldContext;
import net.minecraft.server.network.ServerPlayerEntity;

public interface IPlaystylePlugin {
    String pluginId();
    boolean onLoad(BotContext ctx, WorldContext world, ServerPlayerEntity bot);
    boolean onTick(BotContext ctx, WorldContext world, ServerPlayerEntity bot);
    boolean onUnload(BotContext ctx, WorldContext world, ServerPlayerEntity bot);
}
