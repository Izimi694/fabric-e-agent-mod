package com.izimi.eagent.brainstem.domain;

public sealed interface DomainCommand
        permits BreakCommand, PlaceCommand, CraftCommand,
                CombatCommand, MotionCommand, InventoryCommand {
    String commandType();
}
