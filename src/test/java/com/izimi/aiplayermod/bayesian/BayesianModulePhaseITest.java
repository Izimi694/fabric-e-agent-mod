package com.izimi.aiplayermod.bayesian;

import com.izimi.aiplayermod.util.api.MemoryFileSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BayesianModulePhaseITest {

    private static final UUID BOT_ID = UUID.randomUUID();
    private BayesianModule module;
    private MemoryFileSystem fs;

    @BeforeEach
    void setUp() {
        BayesianModule.resetSharedState();
        fs = new MemoryFileSystem();
        module = new BayesianModule(BOT_ID, fs);
    }

    // ── Environmental Controllability ──

    @Test
    @DisplayName("computeControllability returns 0.5 for unknown reflex")
    void controllabilityUnknown() {
        double c = module.computeControllability("unknown_reflex", null);
        assertEquals(0.5, c, 0.001);
    }

    @Test
    @DisplayName("computeControllability returns >= 0.5 for low-variance reflex")
    void controllabilityHighForLowVariance() {
        for (int i = 0; i < 10; i++) {
            module.update("reflex_dig_stone", List.of(new BayesianFeature("block=stone", true)), true);
        }
        double c = module.computeControllability("reflex_dig_stone", null);
        assertTrue(c >= 0.5, "Low-variance should give controllability >= 0.5, got " + c);
    }

    @Test
    @DisplayName("computeControllability decreases with environment_change feature")
    void controllabilityDecreasesWithEnvChange() {
        for (int i = 0; i < 5; i++) {
            module.update("reflex_test", List.of(new BayesianFeature("test", true)), true);
        }
        double without = module.computeControllability("reflex_test", null);
        double with = module.computeControllability("reflex_test",
                List.of(new BayesianFeature("environment_change", true)));
        assertTrue(with < without, "Environment change should reduce controllability");
    }

    // ── Confidence ──

    @Test
    @DisplayName("getConfidence returns 0.5 for unknown reflex")
    void confidenceUnknown() {
        assertEquals(0.5, module.getConfidence("unknown_reflex"), 0.001);
    }

    @Test
    @DisplayName("getConfidence increases after successful updates")
    void confidenceIncreasesAfterSuccess() {
        for (int i = 0; i < 8; i++) {
            module.update("reflex_mine", List.of(new BayesianFeature("ore=iron", true)), true);
        }
        assertTrue(module.getConfidence("reflex_mine") > 0.5);
    }

    // ── Posterior Stability ──

    @Test
    @DisplayName("isPosteriorStable returns false with few samples")
    void posteriorStableFewSamples() {
        module.update("reflex_test", List.of(new BayesianFeature("x", true)), true);
        assertFalse(module.isPosteriorStable("reflex_test"));
    }

    @Test
    @DisplayName("isPosteriorStable returns true after many consistent updates")
    void posteriorStableManyConsistent() {
        for (int i = 0; i < 8; i++) {
            module.update("reflex_test", List.of(new BayesianFeature("x", true)), true);
        }
        assertTrue(module.isPosteriorStable("reflex_test"));
    }

    @Test
    @DisplayName("isPosteriorStable returns false for fluctuating outcomes")
    void posteriorStableFluctuates() {
        for (int i = 0; i < 10; i++) {
            module.update("reflex_test", List.of(new BayesianFeature("x", true)), i % 2 == 0);
        }
        assertFalse(module.isPosteriorStable("reflex_test"));
    }

    // ── BotState ──

    @Test
    @DisplayName("BotState isRelevantReflex matches currentTask")
    void botStateMatchesTask() {
        var state = new BayesianModule.BotState("mine_iron", null, null, null);
        assertTrue(state.isRelevantReflex("reflex_mine_iron"));
        assertFalse(state.isRelevantReflex("reflex_attack_zombie"));
    }

    @Test
    @DisplayName("BotState isRelevantReflex matches nearbyEntity")
    void botStateMatchesEntity() {
        var state = new BayesianModule.BotState(null, null, "zombie", null);
        assertTrue(state.isRelevantReflex("reflex_attack_zombie"));
    }

    @Test
    @DisplayName("BotState isRelevantReflex matches nearbyBlock")
    void botStateMatchesBlock() {
        var state = new BayesianModule.BotState(null, null, null, "diamond");
        assertTrue(state.isRelevantReflex("reflex_dig_diamond"));
    }

    @Test
    @DisplayName("BotState isRelevantReflex returns false for null reflex")
    void botStateNullReflex() {
        var state = new BayesianModule.BotState("test", null, null, null);
        assertFalse(state.isRelevantReflex(null));
    }
}
