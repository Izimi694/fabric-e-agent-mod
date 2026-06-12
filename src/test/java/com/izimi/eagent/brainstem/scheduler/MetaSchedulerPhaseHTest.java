package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.hormonal.HormonalSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetaSchedulerPhaseHTest {

    // ── Time slice ──

    @Test
    @DisplayName("computeTimeSlice allocates 63.2% of available time")
    void computeTimeSliceAllocates632() {
        long slice = MetaScheduler.computeTimeSlice(1000, 1, 50);
        // available = 1000 - 50 = 950; slice = 950 / 1 * 0.632 ≈ 600
        assertTrue(slice > 500 && slice < 700, "Slice should be ~600ms, got " + slice);
    }

    @Test
    @DisplayName("computeTimeSlice with zero tasks returns totalLatencyBound")
    void computeTimeSliceZeroTasks() {
        assertEquals(100, MetaScheduler.computeTimeSlice(100, 0, 10));
    }

    @Test
    @DisplayName("computeTimeSlice with negative available returns minimum")
    void computeTimeSliceNegativeAvailable() {
        long slice = MetaScheduler.computeTimeSlice(10, 5, 100);
        assertTrue(slice >= 1);
    }

    // ── Preemption ──

    @Test
    @DisplayName("shouldPreempt returns true when new priority exceeds threshold")
    void shouldPreemptExceedsThreshold() {
        double threshold = 1.0 + 1.0 / Math.E;
        assertTrue(MetaScheduler.shouldPreempt(0.5, 0.5 * threshold + 0.01));
    }

    @Test
    @DisplayName("shouldPreempt returns false when new priority is within threshold")
    void shouldPreemptWithinThreshold() {
        assertFalse(MetaScheduler.shouldPreempt(1.0, 1.2));
    }

    @Test
    @DisplayName("shouldPreempt returns false for lower priority")
    void shouldPreemptLowerPriority() {
        assertFalse(MetaScheduler.shouldPreempt(1.0, 0.5));
    }

    // ── DeadEndResult ──

    @Test
    @DisplayName("DeadEndResult records dead-end correctly")
    void deadEndResultRecords() {
        var result = new MetaScheduler.DeadEndResult(true, "CONSECUTIVE_FAILURES");
        assertTrue(result.isDeadEnd());
        assertEquals("CONSECUTIVE_FAILURES", result.reason());
    }

    @Test
    @DisplayName("DeadEndResult records non-dead-end correctly")
    void deadEndResultNonDead() {
        var result = new MetaScheduler.DeadEndResult(false, null);
        assertFalse(result.isDeadEnd());
        assertNull(result.reason());
    }

    // ── RollbackStage ──

    @Test
    @DisplayName("RollbackStage records stage and action")
    void rollbackStageRecords() {
        var stage = new MetaScheduler.RollbackStage(1, "retry");
        assertEquals(1, stage.stage());
        assertEquals("retry", stage.action());
    }

    @Test
    @DisplayName("RollbackStage stage 5 indicates LLM replan")
    void rollbackStageLLMReplan() {
        var stage = new MetaScheduler.RollbackStage(5, "llm_replan");
        assertEquals(5, stage.stage());
        assertEquals("llm_replan", stage.action());
    }

    // ── Wheat-ear static ──

    @Test
    @DisplayName("wheatEarExplore returns 0 when confidence at 0.37")
    void wheatEarExploreAtThreshold() {
        double p = MotivationEngine.wheatEarExplore(1.0 / Math.E, null);
        assertEquals(0.0, p, 0.001);
    }

    @Test
    @DisplayName("wheatEarExplore returns 0.37 when confidence is 0")
    void wheatEarExploreAtZeroConfidence() {
        double p = MotivationEngine.wheatEarExplore(0.0, null);
        assertEquals(1.0 / Math.E, p, 0.001);
    }

    @Test
    @DisplayName("wheatEarExplore returns 0 when confidence exceeds 0.37")
    void wheatEarExploreExceedsThreshold() {
        double p = MotivationEngine.wheatEarExplore(0.5, null);
        assertEquals(0.0, p, 0.001);
    }

    @Test
    @DisplayName("wheatEarExplore increases with curiosity")
    void wheatEarExploreCuriosityBoost() {
        HormonalSystem h = new HormonalSystem(0.1, 0.2, 0.5);
        double withCuriosity = MotivationEngine.wheatEarExplore(0.2, h);
        double withoutCuriosity = MotivationEngine.wheatEarExplore(0.2, null);
        assertTrue(withCuriosity > withoutCuriosity, "Curiosity should boost explore probability");
    }

    @Test
    @DisplayName("wheatEarExplore capped at 1.0")
    void wheatEarExploreCapped() {
        HormonalSystem h = new HormonalSystem(0.1, 0.2, 1.0);
        double p = MotivationEngine.wheatEarExplore(0.0, h);
        assertTrue(p <= 1.0);
    }
}
