package com.izimi.eagent.cortex.api;

public record HormonalPreset(
    double stress,
    double aggression,
    double curiosity,
    double ne,
    double da,
    double serotonin,
    double ach
) {
    public static final HormonalPreset DEFAULT = new HormonalPreset(0.1, 0.2, 0.3, 0.1, 0.2, 0.3, 0.3);

    public HormonalPreset {
        stress = clamp(stress);
        aggression = clamp(aggression);
        curiosity = clamp(curiosity);
        ne = clamp(ne);
        da = clamp(da);
        serotonin = clamp(serotonin);
        ach = clamp(ach);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}
