package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.cortex.prefrontal.CognitiveControl;
import com.izimi.eagent.cortex.prefrontal.ReflexRecipe;
import com.izimi.eagent.hormonal.NeuroState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MetaSchedulerCognitiveControlTest {

    private MetaScheduler metaScheduler;
    private CognitiveControl cognitiveControl;

    @BeforeEach
    void setUp() {
        metaScheduler = new MetaScheduler(new MotivationEngine());
        cognitiveControl = new CognitiveControl();
    }

    // ── MetaScheduler setCognitiveControl ──

    @Test
    @DisplayName("setCognitiveControl accepts null safely")
    void setNullSafely() {
        assertDoesNotThrow(() -> metaScheduler.setCognitiveControl(null));
    }

    @Test
    @DisplayName("setCognitiveControl accepts non-null safely")
    void setNonNullSafely() {
        assertDoesNotThrow(() -> metaScheduler.setCognitiveControl(cognitiveControl));
    }

    // ── CognitiveControl.checkReflex ──

    @Test
    @DisplayName("checkReflex returns null when no recipe registered")
    void checkReflexNoRecipe() {
        assertNull(cognitiveControl.checkReflex("nonexistent", new NeuroState(0.5, 0.5, 0.5, 0.5)));
    }

    @Test
    @DisplayName("checkReflex returns null when all checks pass")
    void checkReflexPasses() {
        var recipe = new ReflexRecipe(
                "test_reflex", new NeuroState(0.7, 0.6, 0.2, 0.8),
                Map.of("da", new ReflexRecipe.NeuroFieldConstraint(0.4, null)),
                3, 1.5
        );
        cognitiveControl.registerRecipe(recipe);
        assertNull(cognitiveControl.checkReflex("test_reflex", new NeuroState(0.7, 0.6, 0.2, 0.8)));
    }

    @Test
    @DisplayName("checkReflex returns reason when requirements not met")
    void checkReflexFailsRequirements() {
        var recipe = new ReflexRecipe(
                "attack_zombie", new NeuroState(0.7, 0.6, 0.2, 0.8),
                Map.of("da", new ReflexRecipe.NeuroFieldConstraint(0.4, null)),
                3, 1.5
        );
        cognitiveControl.registerRecipe(recipe);
        String reason = cognitiveControl.checkReflex("attack_zombie", new NeuroState(0.7, 0.2, 0.2, 0.8));
        assertNotNull(reason);
        assertTrue(reason.contains("requirements not met"));
    }

    @Test
    @DisplayName("checkReflex returns reason when cosine too low")
    void checkReflexFailsCosine() {
        var recipe = new ReflexRecipe(
                "low_cosine", new NeuroState(0.9, 0.9, 0.1, 0.9),
                Map.of(),
                3, 1.5
        );
        cognitiveControl.registerRecipe(recipe);
        String reason = cognitiveControl.checkReflex("low_cosine", new NeuroState(0.1, 0.1, 0.9, 0.1));
        assertNotNull(reason);
        assertTrue(reason.contains("cosine too low"));
    }

    @Test
    @DisplayName("checkReflex with identical vector passes")
    void checkReflexExactMatch() {
        var state = new NeuroState(0.7, 0.6, 0.2, 0.8);
        var recipe = new ReflexRecipe("exact", state, Map.of(), 3, 1.5);
        cognitiveControl.registerRecipe(recipe);
        assertNull(cognitiveControl.checkReflex("exact", state));
    }
}
