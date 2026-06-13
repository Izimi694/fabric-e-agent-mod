package com.izimi.eagent.cortex.prefrontal;

import com.izimi.eagent.hormonal.NeuroDynamics;
import com.izimi.eagent.hormonal.NeuroState;

import java.util.*;
import java.util.stream.Collectors;

public class CognitiveControl {

    private static final double THREAT_THRESHOLD = 0.5;
    private static final double INHIBIT_STRENGTH = 0.6;
    private static final double FLEE_BOOST = 0.4;
    private static final double ATTACK_SUPPRESS = 0.5;

    private final Map<String, ReflexRecipe> recipes = new HashMap<>();

    public void registerRecipe(ReflexRecipe recipe) {
        if (recipe != null && recipe.reflexId() != null) {
            recipes.put(recipe.reflexId(), recipe);
        }
    }

    public void registerRecipes(Collection<ReflexRecipe> recipes) {
        for (ReflexRecipe r : recipes) registerRecipe(r);
    }

    public ReflexRecipe getRecipe(String reflexId) {
        return recipes.get(reflexId);
    }

    /**
     * Check if a single reflex is vetoed by cognitive control.
     * @return null if allowed, or a description string if vetoed.
     */
    public String checkReflex(String reflexId, NeuroState state) {
        ReflexRecipe recipe = recipes.get(reflexId);
        if (recipe == null) return null; // no cognitive constraint

        if (!recipe.meetsRequirements(state)) {
            return "reflex requirements not met (state=" + state + ")";
        }

        double cosine = state.cosineSimilarity(recipe.targetVector());
        if (cosine < 0.3) {
            return "cosine too low (" + String.format("%.2f", cosine) + " < 0.3)";
        }

        return null;
    }

    public double computeInhibition(NeuroState state, String candidateType, String reflexId) {
        double modulation = computeSerotoninModulation(state, candidateType);

        ReflexRecipe recipe = recipes.get(reflexId);
        if (recipe == null) return modulation;

        double cosine = state.cosineSimilarity(recipe.targetVector());
        double cosineMod = cosine * 0.5 - 0.25;

        return clamp(modulation + cosineMod);
    }

    public List<CandidateWeight> modulateCandidates(List<CandidateWeight> candidates, NeuroState state,
                                                     int failureCount, double confidence, double novelty) {
        if (candidates == null || candidates.isEmpty()) return List.of();

        return candidates.stream()
                .map(c -> modulateOne(c, state, failureCount, confidence, novelty))
                .filter(c -> c.weight() > 0)
                .collect(Collectors.toList());
    }

    private CandidateWeight modulateOne(CandidateWeight candidate, NeuroState state,
                                         int failureCount, double confidence, double novelty) {
        String reflexId = candidate.reflexId();
        String type = candidate.type();

        ReflexRecipe recipe = recipes.get(reflexId);

        // Gate ①: 合取条件检查
        if (recipe != null && !recipe.meetsRequirements(state)) {
            return new CandidateWeight(reflexId, type, 0, candidate.recipe());
        }

        // Gate ②: 5-HT 情境分支
        double serotoninMod = computeSerotoninModulation(state, type);

        // Gate ③: 余弦匹配
        double cosineMod = 0;
        if (recipe != null) {
            cosineMod = state.cosineSimilarity(recipe.targetVector());
        }

        // Gate ④: GABA/Glu 分别注入
        double attackInhibition = 0;
        double flightExcitation = 0;
        if ("attack".equals(type)) {
            attackInhibition = NeuroDynamics.computeAttackInhibition(state, failureCount, confidence);
        } else if ("flee".equals(type)) {
            flightExcitation = NeuroDynamics.computeFlightExcitation(state, novelty);
        }

        double newWeight = candidate.weight() * Math.max(0.05, 1.0 + serotoninMod) * cosineMod;
        newWeight *= Math.max(0.1, 1.0 - attackInhibition);
        newWeight += flightExcitation;

        return new CandidateWeight(reflexId, type, Math.max(0, newWeight), candidate.recipe());
    }

    private double computeSerotoninModulation(NeuroState state, String candidateType) {
        if (state.ne() < THREAT_THRESHOLD) {
            return -state.serotonin() * INHIBIT_STRENGTH;
        }
        if ("flee".equals(candidateType)) {
            return +state.serotonin() * FLEE_BOOST;
        } else if ("attack".equals(candidateType)) {
            return -state.serotonin() * ATTACK_SUPPRESS;
        }
        return 0;
    }

    public double getModulation(String thresholdId, NeuroState state) {
        return switch (thresholdId) {
            case "fall_height" -> state.ne() * 3;
            case "lava_distance" -> state.ne() * 2;
            default -> 0;
        };
    }

    public double getEffectiveThreshold(double baseThreshold, String thresholdId, NeuroState state) {
        return baseThreshold + Math.abs(getModulation(thresholdId, state));
    }

    private static double clamp(double v) {
        return Math.max(-1, Math.min(1, v));
    }

    public record CandidateWeight(String reflexId, String type, double weight, ReflexRecipe recipe) {}
}
