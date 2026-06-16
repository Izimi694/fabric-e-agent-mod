package com.izimi.eagent.brainstem.domain;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public record BreakCommand(
        ServerPlayerEntity bot,
        BlockPos target,
        String reason
) implements DomainCommand {
    @Override
    public String commandType() { return "dig"; }
}
