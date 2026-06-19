package com.izimi.eagent.brainstem.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GoalAdoptionTest {

    @Test @DisplayName("pending factory creates goal with initial commitScore")
    void pending() {
        var g = GoalAdoption.pending("iron_ore", 0.5);
        assertEquals("iron_ore", g.attractorType());
        assertEquals(0.5, g.boost());
        assertEquals(0.25, g.commitScore());
        assertEquals(0, g.ticksActive());
        assertFalse(g.isAdopted());
    }

    @Test @DisplayName("isAdopted true when commitScore >= 0.8")
    void adopted() {
        var g = new GoalAdoption("iron_ore", 0.5, 0.9, 100);
        assertTrue(g.isAdopted());
        assertFalse(g.isRejected());
    }

    @Test @DisplayName("isRejected true when commitScore < 0.01")
    void rejected() {
        var g = new GoalAdoption("iron_ore", 0.5, 0.005, 6000);
        assertTrue(g.isRejected());
        assertFalse(g.isAdopted());
    }

    @Test @DisplayName("withCommitIncrease approaches 1.0 asymptotically")
    void commitIncrease() {
        var g = GoalAdoption.pending("iron_ore", 0.5);
        for (int i = 0; i < 200; i++) g = g.withCommitIncrease();
        assertTrue(g.commitScore() > 0.9);
        assertTrue(g.commitScore() < 1.0);
    }

    @Test @DisplayName("withCommitDecay approaches 0.0 exponentially")
    void commitDecay() {
        var g = GoalAdoption.pending("iron_ore", 0.5);
        for (int i = 0; i < 15000; i++) g = g.withCommitDecay();
        assertTrue(g.commitScore() < 0.001);
    }

    @Test @DisplayName("withCommitIncrease increments ticksActive")
    void ticksIncrease() {
        var g = GoalAdoption.pending("test", 0.5).withCommitIncrease();
        assertEquals(1, g.ticksActive());
    }
}
