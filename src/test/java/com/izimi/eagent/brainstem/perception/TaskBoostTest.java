package com.izimi.eagent.brainstem.perception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskBoostTest {

    @Test
    @DisplayName("currentBoost decays exponentially")
    void decay() {
        var b = TaskBoost.of("iron_ore", 1.0);
        assertEquals(1.0, b.currentBoost(0), 0.001);
        double after100 = b.currentBoost(100);
        assertTrue(after100 < 1.0);
        assertTrue(after100 > 0.5); // exp(-0.005*100) = exp(-0.5) ≈ 0.606
        assertEquals(0.606, after100, 0.01);
    }

    @Test
    @DisplayName("isExpired when elapsed >= maxTicks")
    void expiredByTime() {
        var b = new TaskBoost("test", 0.5, TaskBoost.DEFAULT_DECAY, 200);
        assertTrue(b.isExpired(200));
        assertTrue(b.isExpired(300));
    }

    @Test
    @DisplayName("isExpired when boost < 0.01")
    void expiredByBoost() {
        var b = new TaskBoost("test", 0.005, TaskBoost.MIN_DECAY, TaskBoost.MAX_TICKS);
        assertTrue(b.isExpired(0)); // startBoost < 0.01
    }

    @Test
    @DisplayName("constructor clamps bounds")
    void clamps() {
        var b = new TaskBoost("test", 2.0, 0, 0);
        assertEquals(1.0, b.startBoost());
        assertEquals(TaskBoost.MIN_DECAY, b.decayRate());
        assertEquals(TaskBoost.MIN_TICKS, b.maxTicks());
    }

    @Test
    @DisplayName("of factory uses sensible defaults")
    void factoryDefaults() {
        var b = TaskBoost.of("iron_ore", 0.5);
        assertEquals("iron_ore", b.targetType());
        assertEquals(0.5, b.startBoost());
        assertEquals(TaskBoost.DEFAULT_DECAY, b.decayRate());
        assertEquals(TaskBoost.DEFAULT_TICKS, b.maxTicks());
    }

    @Test
    @DisplayName("computeHalfLifeTicks returns ~138 for default decay")
    void halfLife() {
        int hl = TaskBoost.computeHalfLifeTicks(TaskBoost.DEFAULT_DECAY);
        assertEquals(138, hl, 1);
    }
}
