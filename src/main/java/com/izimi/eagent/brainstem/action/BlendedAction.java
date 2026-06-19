package com.izimi.eagent.brainstem.action;

public record BlendedAction(String targetType, double weight, double direction) {
    public BlendedAction {
        if (targetType == null || targetType.isBlank()) throw new IllegalArgumentException("targetType must not be blank");
        weight = Math.max(0, Math.min(1, weight));
        direction = Math.max(-1, Math.min(1, direction));
    }

    public static final BlendedAction NONE = new BlendedAction("none", 0, 0);
}
