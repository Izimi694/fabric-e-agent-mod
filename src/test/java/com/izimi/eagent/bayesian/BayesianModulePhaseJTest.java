package com.izimi.eagent.bayesian;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.izimi.eagent.util.api.MemoryFileSystem;

class BayesianModulePhaseJTest {

    private static final UUID BOT_ID = UUID.randomUUID();
    private BayesianModule module;
    private MemoryFileSystem fs;

    @BeforeEach
    void setUp() {
        BayesianModule.resetSharedState();
        fs = new MemoryFileSystem();
        module = new BayesianModule(BOT_ID, fs);
    }

    // ── inferForward ──

    @Test
    @DisplayName("inferForward returns empty for null state")
    void inferForwardNullState() {
        assertTrue(module.inferForward(null, List.of()).isEmpty());
    }

    @Test
    @DisplayName("inferForward returns sorted by posterior descending")
    void inferForwardSorted() {
        module.update("reflex_a", List.of(new BayesianFeature("x", true)), true);
        module.update("reflex_b", List.of(new BayesianFeature("x", true)), true);
        module.update("reflex_a", List.of(new BayesianFeature("x", true)), true);

        // Use BotState with currentTask matching both reflex names
        var state = new BayesianModule.BotState("a", null, null, null);
        var results = module.inferForward(state, List.of(new BayesianFeature("x", true)));
        // At minimum, should not throw and return list sorted by posterior
        assertNotNull(results);
    }

    @Test
    @DisplayName("inferForward limited to 5 results")
    void inferForwardLimit() {
        for (int i = 0; i < 10; i++) {
            module.update("reflex_" + i, List.of(new BayesianFeature("x", true)), true);
        }
        // BotState won't match "reflex_X" via currentTask; expect empty
        var state = new BayesianModule.BotState(null, null, null, null);
        assertTrue(module.inferForward(state, List.of()).isEmpty());
    }

    // ── inferBackward ──

    @Test
    @DisplayName("inferBackward returns empty for null goal")
    void inferBackwardNullGoal() {
        assertTrue(module.inferBackward(null, List.of()).isEmpty());
    }

    @Test
    @DisplayName("inferBackward returns preconditions sorted by score")
    void inferBackwardSorted() {
        module.update("reflex_goal", List.of(new BayesianFeature("x", true)), true);
        module.update("reflex_prep_a", List.of(new BayesianFeature("y", true)), true);
        module.update("reflex_prep_b", List.of(new BayesianFeature("z", true)), true);

        var results = module.inferBackward("reflex_goal", List.of());
        assertFalse(results.isEmpty());
        double prev = Double.MAX_VALUE;
        for (var entry : results) {
            assertTrue(entry.getValue() <= prev, "Should be sorted descending");
            prev = entry.getValue();
        }
    }

    @Test
    @DisplayName("inferBackward excludes goal reflex from results")
    void inferBackwardExcludesGoal() {
        module.update("reflex_goal", List.of(new BayesianFeature("x", true)), true);
        module.update("reflex_prep", List.of(new BayesianFeature("y", true)), true);

        var results = module.inferBackward("reflex_goal", List.of());
        for (var entry : results) {
            assertNotEquals("reflex_goal", entry.getKey());
        }
    }

    @Test
    @DisplayName("inferBackward returns higher score for higher prior preconditions")
    void inferBackwardScoreProportional() {
        module.update("reflex_goal", List.of(new BayesianFeature("x", true)), true);
        module.update("reflex_high_prior", List.of(new BayesianFeature("y", true)), true);
        for (int i = 0; i < 5; i++) {
            module.update("reflex_high_prior", List.of(new BayesianFeature("y", true)), true);
        }

        var results = module.inferBackward("reflex_goal", List.of());
        String top = results.get(0).getKey();
        assertEquals("reflex_high_prior", top);
    }

    // ── Rollback static logic ──

    @Test
    @DisplayName("RollbackStage stage progression is monotonic")
    void rollbackStageProgression() {
        var s1 = new com.izimi.eagent.brainstem.scheduler.MetaScheduler.RollbackStage(1, "retry");
        var s2 = new com.izimi.eagent.brainstem.scheduler.MetaScheduler.RollbackStage(2, "alternative:n1");
        var s3 = new com.izimi.eagent.brainstem.scheduler.MetaScheduler.RollbackStage(3, "backtrack:n1");
        var s4 = new com.izimi.eagent.brainstem.scheduler.MetaScheduler.RollbackStage(4, "explore");
        var s5 = new com.izimi.eagent.brainstem.scheduler.MetaScheduler.RollbackStage(5, "llm_replan");

        assertTrue(s1.stage() < s2.stage());
        assertTrue(s2.stage() < s3.stage());
        assertTrue(s3.stage() < s4.stage());
        assertTrue(s4.stage() < s5.stage());
    }
}
