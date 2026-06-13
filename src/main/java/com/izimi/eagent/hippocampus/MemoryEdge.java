package com.izimi.eagent.hippocampus;

import java.util.Objects;

public class MemoryEdge {
    private final String fromId;
    private final String toId;
    private final RelationType type;
    private double weight;
    private long lastReinforcedAt;
    private final long createdAt;

    public MemoryEdge(String fromId, String toId, RelationType type, double weight) {
        this(fromId, toId, type, weight, System.currentTimeMillis(), System.currentTimeMillis());
    }

    MemoryEdge(String fromId, String toId, RelationType type, double weight, long createdAt, long lastReinforcedAt) {
        this.fromId = Objects.requireNonNull(fromId, "fromId must not be null");
        this.toId = Objects.requireNonNull(toId, "toId must not be null");
        this.type = Objects.requireNonNull(type, "type must not be null");
        this.weight = Math.max(0.1, Math.min(1.0, weight));
        this.createdAt = createdAt;
        this.lastReinforcedAt = lastReinforcedAt;
    }

    public String fromId() { return fromId; }
    public String toId() { return toId; }
    public RelationType type() { return type; }
    public double weight() { return weight; }
    public long createdAt() { return createdAt; }
    public long lastReinforcedAt() { return lastReinforcedAt; }

    public void updateWeight(double delta) {
        this.weight = Math.max(0.1, Math.min(1.0, this.weight + delta));
        this.lastReinforcedAt = System.currentTimeMillis();
    }

    public void setWeight(double weight) {
        this.weight = Math.max(0.1, Math.min(1.0, weight));
    }

    public void setLastReinforcedAt(long timestamp) {
        this.lastReinforcedAt = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MemoryEdge that)) return false;
        return fromId.equals(that.fromId) && toId.equals(that.toId) && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromId, toId, type);
    }

    @Override
    public String toString() {
        return "MemoryEdge{" + fromId + "->" + toId + ":" + type + "(" + weight + ")}";
    }

    public enum RelationType {
        CAUSAL,
        TEMPORAL,
        SIMILARITY,
        CONTRAST
    }
}
