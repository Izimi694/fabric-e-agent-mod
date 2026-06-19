package com.izimi.eagent.brainstem.domain;

public sealed interface DomainCommand
        permits BreakCommand, PlaceCommand, CraftCommand,
                CombatCommand, MotionCommand, InventoryCommand, GenericCommand {
    String commandType();
}
