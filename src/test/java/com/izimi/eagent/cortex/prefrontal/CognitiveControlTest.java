package com.izimi.eagent.cortex.prefrontal;

import com.izimi.eagent.hormonal.NeuroState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CognitiveControlTest {

    private CognitiveControl cc;
    private NeuroState attackState;
    private NeuroState fleeState;
    private NeuroState exploreState;

    @BeforeEach
    void setUp() {
        cc = new CognitiveControl();

        attackState = new NeuroState(0.7, 0.6, 0.2, 0.8);
        fleeState = new NeuroState(0.8, 0.2, 0.7, 0.2);
        exploreState = new NeuroState(0.2, 0.7, 0.7, 0.2);

        var attackRecipe = new ReflexRecipe(
                "attack_zombie", attackState,
                Map.of("da", new ReflexRecipe.NeuroFieldConstraint(0.4, null),
                       "serotonin", new ReflexRecipe.NeuroFieldConstraint(null, 0.3)),
                3, 1.5
        );
        var fleeRecipe = new ReflexRecipe("flee_danger", fleeState, Map.of(), 2, 1.0);
        var exploreRecipe = new ReflexRecipe("explore_cave", exploreState, Map.of(), 1, 0.5);

        cc.registerRecipes(List.of(attackRecipe, fleeRecipe, exploreRecipe));
    }

    @Test
    @DisplayName("registerRecipe stores and retrieves recipe")
    void registerAndGet() {
        var r = cc.getRecipe("attack_zombie");
        assertNotNull(r);
        assertEquals("attack_zombie", r.reflexId());
        assertNull(cc.getRecipe("nonexistent"));
    }

    @Test
    @DisplayName("meetsRequirements passes when state satisfies constraints")
    void meetsRequirementsPass() {
        var recipe = cc.getRecipe("attack_zombie");
        assertNotNull(recipe);
        assertTrue(recipe.meetsRequirements(attackState));
    }

    @Test
    @DisplayName("meetsRequirements fails when DA too low")
    void meetsRequirementsFailsLowDA() {
        var recipe = cc.getRecipe("attack_zombie");
        assertNotNull(recipe);
        var lowDA = new NeuroState(0.7, 0.2, 0.2, 0.8);
        assertFalse(recipe.meetsRequirements(lowDA));
    }

    @Test
    @DisplayName("meetsRequirements fails when serotonin too high")
    void meetsRequirementsFailsHighSerotonin() {
        var recipe = cc.getRecipe("attack_zombie");
        assertNotNull(recipe);
        var highSer = new NeuroState(0.7, 0.6, 0.8, 0.8);
        assertFalse(recipe.meetsRequirements(highSer));
    }

    @Test
    @DisplayName("modulateCandidates filters out candidates failing require")
    void modulateFiltersOut() {
        var candidates = List.of(
                new CognitiveControl.CandidateWeight("attack_zombie", "attack", 1.0, cc.getRecipe("attack_zombie")),
                new CognitiveControl.CandidateWeight("flee_danger", "flee", 0.8, cc.getRecipe("flee_danger"))
        );
        var highSerState = new NeuroState(0.7, 0.6, 0.8, 0.8);
        var result = cc.modulateCandidates(candidates, highSerState, 0, 0.5, 0.2);
        assertEquals(1, result.size());
        assertEquals("flee_danger", result.get(0).reflexId());
    }

    @Test
    @DisplayName("5-HT low threat: suppresses all candidates")
    void serotoninLowThreatSuppresses() {
        var state = new NeuroState(0.2, 0.3, 0.8, 0.3);
        var candidates = List.of(
                new CognitiveControl.CandidateWeight("attack_zombie", "attack", 1.0, cc.getRecipe("attack_zombie")),
                new CognitiveControl.CandidateWeight("flee_danger", "flee", 0.8, cc.getRecipe("flee_danger")),
                new CognitiveControl.CandidateWeight("explore_cave", "explore", 0.6, cc.getRecipe("explore_cave"))
        );
        var result = cc.modulateCandidates(candidates, state, 0, 0.5, 0.2);
        for (var c : result) {
            assertTrue(c.weight() < 1.0, "Low threat with high 5-HT should suppress, got " + c.weight());
        }
    }

    @Test
    @DisplayName("5-HT high threat: boosts flee, suppresses attack")
    void serotoninHighThreatModulates() {
        var state = new NeuroState(0.7, 0.3, 0.7, 0.3);
        var candidates = List.of(
                new CognitiveControl.CandidateWeight("attack_zombie", "attack", 1.0, cc.getRecipe("attack_zombie")),
                new CognitiveControl.CandidateWeight("flee_danger", "flee", 0.8, cc.getRecipe("flee_danger"))
        );
        var result = cc.modulateCandidates(candidates, state, 0, 0.5, 0.2);
        double fleeWeight = 0, attackWeight = 0;
        for (var c : result) {
            if ("flee".equals(c.type())) fleeWeight = c.weight();
            if ("attack".equals(c.type())) attackWeight = c.weight();
        }
        assertTrue(fleeWeight > attackWeight,
                "High threat: flee should be higher than attack: flee=" + fleeWeight + " attack=" + attackWeight);
    }

    @Test
    @DisplayName("getModulation returns non-negative values for known thresholds")
    void getModulationKnown() {
        var state = new NeuroState(0.8, 0.5, 0.3, 0.5);
        assertTrue(cc.getModulation("fall_height", state) >= 0);
        assertTrue(cc.getModulation("lava_distance", state) >= 0);
        assertEquals(0, cc.getModulation("unknown", state));
    }

    @Test
    @DisplayName("getEffectiveThreshold is greater or equal to base")
    void effectiveThreshold() {
        var state = new NeuroState(0.8, 0.5, 0.3, 0.5);
        assertTrue(cc.getEffectiveThreshold(5, "fall_height", state) >= 5);
        assertTrue(cc.getEffectiveThreshold(3, "lava_distance", state) >= 3);
    }

    @Test
    @DisplayName("cosine similarity 4D > 3D for attack vs explore")
    void cosine4DBetter() {
        double cos4d = attackState.cosineSimilarity(exploreState);
        double dot3 = attackState.ne() * exploreState.ne() + attackState.da() * exploreState.da() + attackState.serotonin() * exploreState.serotonin();
        double m1 = Math.sqrt(attackState.ne()*attackState.ne() + attackState.da()*attackState.da() + attackState.serotonin()*attackState.serotonin());
        double m2 = Math.sqrt(exploreState.ne()*exploreState.ne() + exploreState.da()*exploreState.da() + exploreState.serotonin()*exploreState.serotonin());
        double cos3d = (m1 == 0 || m2 == 0) ? 0 : dot3 / (m1 * m2);
        assertTrue(cos4d < cos3d, "4D should be more discriminative: 4D=" + cos4d + " 3D=" + cos3d);
    }
}
