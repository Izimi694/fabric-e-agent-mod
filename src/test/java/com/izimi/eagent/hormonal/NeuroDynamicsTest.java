package com.izimi.eagent.hormonal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeuroDynamicsTest {

    @Test
    @DisplayName("computeAttackInhibition increases with serotonin")
    void attackInhibitionSerotonin() {
        double low = NeuroDynamics.computeAttackInhibition(0.1, 0, 0.5);
        double high = NeuroDynamics.computeAttackInhibition(0.9, 0, 0.5);
        assertTrue(high > low, "Higher serotonin should increase attack inhibition");
    }

    @Test
    @DisplayName("computeAttackInhibition increases with failure count")
    void attackInhibitionFailures() {
        double noFail = NeuroDynamics.computeAttackInhibition(0.3, 0, 0.5);
        double manyFail = NeuroDynamics.computeAttackInhibition(0.3, 10, 0.5);
        assertTrue(manyFail > noFail, "More failures should increase attack inhibition");
    }

    @Test
    @DisplayName("computeAttackInhibition is capped at 0.9")
    void attackInhibitionCap() {
        double result = NeuroDynamics.computeAttackInhibition(1.0, 100, 0.0);
        assertEquals(0.9, result, 0.001);
    }

    @Test
    @DisplayName("computeFlightExcitation increases with da and ne")
    void flightExcitationDAandNE() {
        double low = NeuroDynamics.computeFlightExcitation(0.0, 0.0, 0.0);
        double high = NeuroDynamics.computeFlightExcitation(1.0, 1.0, 0.5);
        assertTrue(high > low);
    }

    @Test
    @DisplayName("computeFlightExcitation increases with novelty")
    void flightExcitationNovelty() {
        double low = NeuroDynamics.computeFlightExcitation(0.3, 0.3, 0.0);
        double high = NeuroDynamics.computeFlightExcitation(0.3, 0.3, 1.0);
        assertTrue(high > low, "Higher novelty should increase flight excitation");
    }

    @Test
    @DisplayName("computeFlightExcitation is capped at 0.9")
    void flightExcitationCap() {
        double result = NeuroDynamics.computeFlightExcitation(1.0, 1.0, 1.0);
        assertEquals(0.9, result, 0.001);
    }

    @Test
    @DisplayName("overload with NeuroState delegates correctly")
    void neuroStateOverload() {
        var state = new NeuroState(0.3, 0.4, 0.5, 0.6);
        double direct = NeuroDynamics.computeAttackInhibition(0.5, 3, 0.7);
        double viaState = NeuroDynamics.computeAttackInhibition(state, 3, 0.7);
        assertEquals(direct, viaState, 0.001);

        double directFlight = NeuroDynamics.computeFlightExcitation(0.4, 0.3, 0.2);
        double viaStateFlight = NeuroDynamics.computeFlightExcitation(state, 0.2);
        assertEquals(directFlight, viaStateFlight, 0.001);
    }
}
