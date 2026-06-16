package com.izimi.eagent.brainstem.domain;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

public record PlaceCommand(
        ServerPlayerEntity bot,
        BlockPos pos,
        String face,
        String reason
) implements DomainCommand {
    @Override
    public String commandType() { return "placeBlock"; }
}
