package com.izimi.eagent.brainstem.domain;

import com.izimi.eagent.brainstem.action.BlendedAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainRouterCPGTest {
    final DomainRouter router = new DomainRouter();

    @Test @DisplayName("executeBlended with tier passes precision to command")
    void blendedWithTier() {
        assertTrue(router.executeBlended(new BlendedAction("dig_iron", 0.8, 0), "HIGH"));
    }

    @Test @DisplayName("getLocalBus returns NEUTRAL")
    void localBus() {
        assertEquals(DomainSignal.NEUTRAL, router.getLocalBus());
    }

    @Test @DisplayName("CPGs exist after construction")
    void cpgAccess() {
        assertNotNull(router.getMotionCPG());
        assertNotNull(router.getDigCPG());
        assertNotNull(router.getCombatCPG());
    }

    @Test @DisplayName("tickAll increments CPG phases")
    void tickAllAdvances() {
        var motion = router.getMotionCPG();
        int before = motion.getPhaseTick();
        router.tickAll();
        assertEquals(before + 1, motion.getPhaseTick());
    }
}
