package com.izimi.eagent.brainstem.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutorCPGTest {

    @Test @DisplayName("MotionCPG walk phase cycles")
    void motionWalkPhase() {
        var cpg = new ExecutorCPG.MotionCPG();
        assertEquals(0, cpg.getPhaseTick());
        cpg.tickPhase();
        assertEquals(1, cpg.getPhaseTick());
    }

    @Test @DisplayName("MotionCPG canJump returns true initially")
    void motionCanJump() {
        var cpg = new ExecutorCPG.MotionCPG();
        assertTrue(cpg.canJump());
        cpg.onJump();
        assertFalse(cpg.canJump());
        for (int i = 0; i < 10; i++) cpg.tickPhase();
        assertTrue(cpg.canJump());
    }

    @Test @DisplayName("MotionCPG isSprintingRecommended returns true at high urgency")
    void motionSprint() {
        var cpg = new ExecutorCPG.MotionCPG();
        cpg.tickPhase();
        boolean rec1 = cpg.isSprintingRecommended(0.6);
        cpg.tickPhase();
        cpg.tickPhase();
        boolean rec2 = cpg.isSprintingRecommended(0.6);
        assertNotEquals(rec1, rec2);
    }

    @Test @DisplayName("DigCPG shouldSwing at interval")
    void digSwing() {
        var cpg = new ExecutorCPG.DigCPG();
        assertFalse(cpg.shouldSwing());
        for (int i = 0; i < 7; i++) cpg.tickPhase();
        assertTrue(cpg.shouldSwing());
        cpg.onSwing();
        assertFalse(cpg.shouldSwing());
    }

    @Test @DisplayName("DigCPG break progress")
    void digProgress() {
        var cpg = new ExecutorCPG.DigCPG();
        double p = cpg.getBreakProgress(5, 20);
        assertEquals(0.25, p, 1e-6);
    }

    @Test @DisplayName("CombatCPG attack cooldown")
    void combatCooldown() {
        var cpg = new ExecutorCPG.CombatCPG();
        assertTrue(cpg.canAttack());
        cpg.onAttack();
        assertFalse(cpg.canAttack());
        for (int i = 0; i < 10; i++) cpg.tickPhase();
        assertTrue(cpg.canAttack());
    }

    @Test @DisplayName("resetPhase resets tick counter to 0")
    void reset() {
        var cpg = new ExecutorCPG.DigCPG();
        cpg.tickPhase();
        cpg.resetPhase();
        assertEquals(0, cpg.getPhaseTick());
    }
}
