package com.izimi.eagent.brainstem.action;

public record MemoryHighlight(
    String eventType,
    String summary,
    double salience,
    long ticksAgo
) {
    public MemoryHighlight {
        eventType = eventType == null ? "" : eventType;
        summary = summary == null ? "" : summary;
        salience = Math.max(0, Math.min(1, salience));
        ticksAgo = Math.max(0, ticksAgo);
    }
}
