package com.izimi.eagent.brainstem.action;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public interface IAction {
    ActionState tick(ServerWorld world, ServerPlayerEntity bot);
    void reset();
}
