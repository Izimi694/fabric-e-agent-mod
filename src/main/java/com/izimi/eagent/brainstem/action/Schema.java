package com.izimi.eagent.brainstem.action;

public record Schema(
    String id,
    String label,
    int gridX,
    int gridZ,
    double centerX,
    double centerZ,
    double avgSatisfaction,
    int pointCount,
    int consecutiveHiCount,
    int consecutiveLoCount,
    boolean permanent
) {
    public Schema {
        id = id == null ? "" : id;
        label = label == null ? "" : label;
        permanent = consecutiveHiCount >= 3;
    }

    public Schema withConsecutiveHi(int val) {
        return new Schema(id, label, gridX, gridZ, centerX, centerZ, avgSatisfaction, pointCount, val, 0, val >= 3);
    }

    public Schema withConsecutiveLo(int val) {
        return new Schema(id, label, gridX, gridZ, centerX, centerZ, avgSatisfaction, pointCount, consecutiveHiCount, val, permanent);
    }

    public boolean canBeRemoved() {
        return !permanent && consecutiveLoCount >= 2;
    }

    public static String gridKey(int gridX, int gridZ) {
        return "g:" + gridX + ":" + gridZ;
    }

    public static int gridFromCoord(double coord) {
        return (int) Math.floor(coord / 4.0);
    }
}
