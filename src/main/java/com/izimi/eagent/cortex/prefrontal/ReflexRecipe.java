package com.izimi.eagent.cortex.prefrontal;

import com.izimi.eagent.hormonal.NeuroState;

import java.util.Collections;
import java.util.Map;

public record ReflexRecipe(
        String reflexId,
        NeuroState targetVector,
        Map<String, NeuroFieldConstraint> require,
        double safetyDistance,
        double neModulation
) {

    public static final double DEFAULT_SAFETY_DISTANCE = 3;
    public static final double DEFAULT_NE_MODULATION = 1.5;

    public ReflexRecipe {
        require = require != null ? Map.copyOf(require) : Collections.emptyMap();
    }

    public boolean meetsRequirements(NeuroState state) {
        for (var entry : require.entrySet()) {
            double value = state.getValue(entry.getKey());
            NeuroFieldConstraint constraint = entry.getValue();
            if (constraint.min != null && value < constraint.min) return false;
            if (constraint.max != null && value > constraint.max) return false;
        }
        return true;
    }

    public record NeuroFieldConstraint(Double min, Double max) {
        public boolean hasMin() { return min != null; }
        public boolean hasMax() { return max != null; }
    }
}
