package com.izimi.aiplayermod.amygdala;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionedReflexPhaseLTest {

    // ── PreconditionResult record tests (no file I/O needed) ──

    @Test
    @DisplayName("PreconditionResult passed=true record")
    void preconditionResultPassed() {
        var pr = new ConditionedReflex.PreconditionResult(true, null, "skip");
        assertTrue(pr.passed());
        assertNull(pr.reason());
        assertEquals("skip", pr.failStrategy());
    }

    @Test
    @DisplayName("PreconditionResult passed=false with reason")
    void preconditionResultFailed() {
        var pr = new ConditionedReflex.PreconditionResult(false, "precondition_failed:state.stress", "wait");
        assertFalse(pr.passed());
        assertEquals("precondition_failed:state.stress", pr.reason());
        assertEquals("wait", pr.failStrategy());
    }

    @Test
    @DisplayName("PreconditionResult fail_strategy can be defer")
    void preconditionResultDefer() {
        var pr = new ConditionedReflex.PreconditionResult(false, "item.missing", "defer");
        assertFalse(pr.passed());
        assertEquals("defer", pr.failStrategy());
    }

    @Test
    @DisplayName("PreconditionResult fail_strategy defaults to skip")
    void preconditionResultDefaultSkip() {
        var pr = new ConditionedReflex.PreconditionResult(true, null, "skip");
        assertEquals("skip", pr.failStrategy());
    }
}
