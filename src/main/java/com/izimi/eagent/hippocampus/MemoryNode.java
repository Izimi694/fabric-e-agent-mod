package com.izimi.eagent.hippocampus;

import java.util.Map;

public record MemoryNode(String memoryId, String summary, long timestamp, int gameDay, Map<String, Object> metadata) {
    public MemoryNode(String memoryId, String summary, long timestamp, int gameDay) {
        this(memoryId, summary, timestamp, gameDay, Map.of());
    }
}
