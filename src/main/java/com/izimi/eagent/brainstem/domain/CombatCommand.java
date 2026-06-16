package com.izimi.eagent.brainstem.domain;

import net.minecraft.server.network.ServerPlayerEntity;

public record CombatCommand(
        ServerPlayerEntity bot,
        String entityName,
        String reason
) implements DomainCommand {
    @Override
    public String commandType() { return "attack"; }
}
