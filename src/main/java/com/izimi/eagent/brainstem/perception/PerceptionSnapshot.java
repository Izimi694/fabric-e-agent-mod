package com.izimi.eagent.brainstem.perception;

public record PerceptionSnapshot(
    long tick,
    DenseView dense,
    CompactView compact
) {
    public record DenseView(
        int oreVeinsNearby,
        int woodBlocksNearby,
        int mobCount,
        int incomingProjectiles,
        double health,
        double hunger,
        double armor,
        boolean isUnderAttack,
        boolean hasShelterNearby,
        double timeOfDay,
        double controllableIndex
    ) {
        public DenseView {
            health = clamp01(health);
            hunger = clamp01(hunger);
            armor = clamp01(armor);
            controllableIndex = clamp01(controllableIndex);
            timeOfDay = Math.max(0, Math.min(24000, timeOfDay));
            oreVeinsNearby = Math.max(0, oreVeinsNearby);
            woodBlocksNearby = Math.max(0, woodBlocksNearby);
            mobCount = Math.max(0, mobCount);
            incomingProjectiles = Math.max(0, incomingProjectiles);
        }
    }

    public record CompactView(String summary) {
        public CompactView {
            summary = summary == null ? "" : summary;
        }
    }

    public static PerceptionSnapshot empty(long tick) {
        return new PerceptionSnapshot(
            tick,
            new DenseView(0, 0, 0, 0, 1.0, 1.0, 1.0, false, true, 0, 0),
            new CompactView("")
        );
    }

    static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
