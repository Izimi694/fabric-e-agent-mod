package com.izimi.eagent.brainstem.domain;

public record GenericCommand(String commandType, String targetType, int priority, double direction, double precision) implements DomainCommand {
    public GenericCommand {
        if (commandType == null || commandType.isBlank()) commandType = "idle";
        if (targetType == null) targetType = "none";
        precision = Math.max(0.5, Math.min(5.0, precision));
    }

    public GenericCommand(String commandType, String targetType, int priority, double direction) {
        this(commandType, targetType, priority, direction, 2.0);
    }
}
