package com.izimi.eagent.brainstem.action;

import com.izimi.eagent.brainstem.perception.TaskBoost;

public record LandscapePatch(
    Attractor attractor,
    Repulsor repulsor,
    String diagnosis,
    String recommendation,
    ConditionSpec conditionSpec,
    String dimensionBias,
    Integer yLevelTarget
) {
    public LandscapePatch {
        if (attractor == null && repulsor == null) {
            throw new IllegalArgumentException("Must provide at least one attractor or repulsor");
        }
        diagnosis = diagnosis == null ? "" : diagnosis;
        recommendation = recommendation == null ? "" : recommendation;
    }

    public record Attractor(
        String type,
        String category,
        java.util.List<String> semanticLabels,
        double salienceBoost,
        double decayRate,
        int maxTicks
    ) {
        public Attractor {
            int count = 0;
            if (type != null) count++;
            if (category != null) count++;
            if (semanticLabels != null && !semanticLabels.isEmpty()) count++;
            if (count != 1) {
                throw new IllegalArgumentException("exactly one of type/category/semanticLabels required");
            }
            salienceBoost = Math.max(0, Math.min(1, salienceBoost));
            decayRate = Math.max(TaskBoost.MIN_DECAY, Math.min(TaskBoost.MAX_DECAY, decayRate));
            maxTicks = Math.max(TaskBoost.MIN_TICKS, Math.min(TaskBoost.MAX_TICKS, maxTicks));
        }

        public boolean isCategory() { return category != null; }
        public boolean isSemantic() { return semanticLabels != null && !semanticLabels.isEmpty(); }

        public static Attractor ofType(String type, double boost) {
            return new Attractor(type, null, null, boost, TaskBoost.DEFAULT_DECAY, TaskBoost.DEFAULT_TICKS);
        }

        public static Attractor ofCategory(String category, double boost) {
            return new Attractor(null, category, null, boost, TaskBoost.DEFAULT_DECAY, TaskBoost.DEFAULT_TICKS);
        }

        public static Attractor ofLabels(java.util.List<String> labels, double boost) {
            return new Attractor(null, null, labels, boost, TaskBoost.DEFAULT_DECAY, TaskBoost.DEFAULT_TICKS);
        }
    }

    public record Repulsor(
        String type,
        String category,
        double salienceReduction,
        double decayRate,
        int maxTicks
    ) {
        public Repulsor {
            if (type == null && category == null) {
                throw new IllegalArgumentException("type and category cannot both be null");
            }
            if (type != null && category != null) {
                throw new IllegalArgumentException("type and category are mutually exclusive");
            }
            salienceReduction = Math.max(0, Math.min(1, salienceReduction));
            decayRate = Math.max(TaskBoost.MIN_DECAY, Math.min(TaskBoost.MAX_DECAY, decayRate));
            maxTicks = Math.max(TaskBoost.MIN_TICKS, Math.min(TaskBoost.MAX_TICKS, maxTicks));
        }
    }

    public record ConditionSpec(
        String conditionType,
        String item,
        double threshold,
        boolean negate,
        double thenMultiplier
    ) {
        public ConditionSpec {
            conditionType = conditionType == null ? "" : conditionType;
            thenMultiplier = Math.max(0.5, Math.min(5.0, thenMultiplier));
            threshold = Math.max(0, Math.min(1, threshold));
        }
    }

    public String targetKey() {
        if (attractor != null) {
            if (attractor.type != null) return attractor.type;
            if (attractor.category != null) return attractor.category;
            if (attractor.isSemantic()) return attractor.semanticLabels.get(0);
        }
        if (repulsor != null) {
            if (repulsor.type != null) return repulsor.type;
            if (repulsor.category != null) return repulsor.category;
        }
        return "unknown";
    }

    public static LandscapePatch attractOnly(Attractor a) {
        return new LandscapePatch(a, null, null, null, null, null, null);
    }

    public static LandscapePatch repulseOnly(Repulsor r) {
        return new LandscapePatch(null, r, null, null, null, null, null);
    }

    public static LandscapePatch empty() {
        return new LandscapePatch(null, null, null, null, null, null, null);
    }
}
