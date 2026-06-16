package com.izimi.eagent.brainstem.domain;

import net.minecraft.server.network.ServerPlayerEntity;

public record CraftCommand(
        ServerPlayerEntity bot,
        String itemId,
        String reason
) implements DomainCommand {
    @Override
    public String commandType() { return "craft"; }
}
