package com.izimi.eagent.brainstem.action;

import com.izimi.eagent.hormonal.NeuroState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CompressedStateTest {

    @Test
    @DisplayName("idle has safe defaults")
    void idle() {
        var s = CompressedState.idle();
        assertEquals("ROUTINE", s.currentPerspective());
        assertEquals("IDLE", s.dominantDomain());
        assertEquals("", s.activeGoalSummary());
        assertEquals(NeuroState.neutral(), s.hormones());
    }

    @Test
    @DisplayName("constructor clamps survivalUrgency")
    void clampsUrgency() {
        var s = new CompressedState("TASK", "MINING", "get iron", NeuroState.zero(), 100, 1.5);
        assertEquals(1.0, s.survivalUrgency());
    }

    @Test
    @DisplayName("constructor provides default values for nulls")
    void nullDefaults() {
        var s = new CompressedState(null, null, null, NeuroState.zero(), 0, 0);
        assertEquals("ROUTINE", s.currentPerspective());
        assertEquals("IDLE", s.dominantDomain());
        assertEquals("", s.activeGoalSummary());
    }
}
