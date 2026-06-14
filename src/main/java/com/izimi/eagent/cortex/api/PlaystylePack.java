package com.izimi.eagent.cortex.api;

import java.util.Collections;
import java.util.Map;

public record PlaystylePack(
    String packName,
    String description,
    int version,
    PackProfile profile,
    Map<String, Object> reflexes,
    Map<String, Object> knowledge,
    Map<String, Object> config,
    String persona
) {
    public static final int CURRENT_VERSION = 2;

    public PlaystylePack {
        if (reflexes == null) reflexes = Collections.emptyMap();
        if (knowledge == null) knowledge = Collections.emptyMap();
        if (config == null) config = Collections.emptyMap();
        if (persona == null) persona = "";
    }

    public record PackProfile(
        double alpha,
        double beta,
        double temperature,
        HormonalPreset hormonalPreset,
        Map<String, Double> perspectiveWeights
    ) {
        public PackProfile {
            if (hormonalPreset == null) hormonalPreset = HormonalPreset.DEFAULT;
            if (perspectiveWeights == null) perspectiveWeights = Collections.emptyMap();
        }
    }
}
