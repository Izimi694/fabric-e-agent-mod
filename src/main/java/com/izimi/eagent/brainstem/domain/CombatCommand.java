package com.izimi.eagent.brainstem.domain;

import net.minecraft.server.network.ServerPlayerEntity;

public record CombatCommand(
        ServerPlayerEntity bot,
        String entityName,
        String reason,
        String mode
) implements DomainCommand {
    public CombatCommand(ServerPlayerEntity bot, String entityName, String reason) {
        this(bot, entityName, reason, "melee");
    }

    @Override
    public String commandType() { return "attack"; }
}
