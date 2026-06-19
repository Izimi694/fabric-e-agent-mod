package com.izimi.eagent.brainstem.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlendedActionTest {
    @Test @DisplayName("constructor clamps weight to [0,1]")
    void clampsWeight() {
        assertEquals(0, new BlendedAction("test", -0.5, 0).weight());
        assertEquals(1, new BlendedAction("test", 1.5, 0).weight());
    }
    @Test @DisplayName("constructor clamps direction to [-1,1]")
    void clampsDirection() {
        assertEquals(-1, new BlendedAction("test", 0.5, -2).direction());
        assertEquals(1, new BlendedAction("test", 0.5, 2).direction());
    }
    @Test @DisplayName("rejects blank targetType")
    void rejectsBlank() {
        assertThrows(IllegalArgumentException.class, () -> new BlendedAction("", 0.5, 0));
    }
    @Test @DisplayName("NONE is safe default")
    void none() {
        assertEquals("none", BlendedAction.NONE.targetType());
        assertEquals(0, BlendedAction.NONE.weight());
    }
}
