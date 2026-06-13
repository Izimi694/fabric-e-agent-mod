package com.izimi.eagent.hormonal;

public record NeuroState(double ne, double da, double serotonin, double ach) {

    public NeuroState {
        ne = clamp(ne);
        da = clamp(da);
        serotonin = clamp(serotonin);
        ach = clamp(ach);
    }

    public double cosineSimilarity(NeuroState other) {
        double dot = ne * other.ne + da * other.da + serotonin * other.serotonin + ach * other.ach;
        double mag1 = Math.sqrt(ne * ne + da * da + serotonin * serotonin + ach * ach);
        double mag2 = Math.sqrt(other.ne * other.ne + other.da * other.da + other.serotonin * other.serotonin + other.ach * other.ach);
        if (mag1 == 0 || mag2 == 0) return 0;
        return dot / (mag1 * mag2);
    }

    public double getValue(String field) {
        return switch (field) {
            case "ne", "norepinephrine" -> ne;
            case "da", "dopamine" -> da;
            case "serotonin", "5ht", "5-ht" -> serotonin;
            case "ach", "acetylcholine" -> ach;
            default -> throw new IllegalArgumentException("Unknown neuro field: " + field);
        };
    }

    public NeuroState withNE(double ne)     { return new NeuroState(ne, da, serotonin, ach); }
    public NeuroState withDA(double da)     { return new NeuroState(ne, da, serotonin, ach); }
    public NeuroState withSerotonin(double s) { return new NeuroState(ne, da, s, ach); }
    public NeuroState withACh(double ach)   { return new NeuroState(ne, da, serotonin, ach); }

    public static NeuroState zero() { return new NeuroState(0, 0, 0, 0); }
    public static NeuroState neutral() { return new NeuroState(0.3, 0.3, 0.3, 0.3); }

    private static double clamp(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
