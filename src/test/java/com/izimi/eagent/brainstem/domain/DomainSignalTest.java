package com.izimi.eagent.brainstem.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainSignalTest {
    @Test @DisplayName("NEUTRAL has safe defaults")
    void neutral() {
        assertEquals(0, DomainSignal.NEUTRAL.movementIntensity());
        assertFalse(DomainSignal.NEUTRAL.isInCombatRange());
    }

    @Test @DisplayName("withMovementIntensity clamps to [0,1]")
    void clampsIntensity() {
        var s = DomainSignal.NEUTRAL.withMovementIntensity(-0.5);
        assertEquals(0, s.movementIntensity());
        var s2 = DomainSignal.NEUTRAL.withMovementIntensity(1.5);
        assertEquals(1.0, s2.movementIntensity());
    }

    @Test @DisplayName("withCombatRange sets combat flag")
    void combatRange() {
        var s = DomainSignal.NEUTRAL.withCombatRange(true);
        assertTrue(s.isInCombatRange());
    }

    @Test @DisplayName("withBodyYaw sets yaw")
    void bodyYaw() {
        var s = DomainSignal.NEUTRAL.withBodyYaw(45.0f);
        assertEquals(45.0f, s.bodyYaw());
    }
}
