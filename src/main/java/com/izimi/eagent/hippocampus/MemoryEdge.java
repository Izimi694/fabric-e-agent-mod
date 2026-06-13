package com.izimi.eagent.hippocampus;

public record MemoryEdge(String fromId, String toId, RelationType type, double weight) {

    public enum RelationType {
        CAUSAL,
        TEMPORAL,
        SIMILARITY,
        CONTRAST
    }
}
