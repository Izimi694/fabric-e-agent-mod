package com.izimi.aiplayermod.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SharedPoolConfigTest {

    @Test
    @DisplayName("CHAIN_MAX_LENGTH is 5")
    void chainMaxLength() {
        assertEquals(5, SharedPoolConfig.CHAIN_MAX_LENGTH);
    }

    @Test
    @DisplayName("BAYESIAN_CANDIDATE_LIMIT is 5")
    void bayesianCandidateLimit() {
        assertEquals(5, SharedPoolConfig.BAYESIAN_CANDIDATE_LIMIT);
    }

    @Test
    @DisplayName("SHARED_PRIOR_RATIO is 0.20")
    void sharedPriorRatio() {
        assertEquals(0.20, SharedPoolConfig.SHARED_PRIOR_RATIO, 0.001);
    }

    @Test
    @DisplayName("SHARED_PRIOR_RATIO_MIN is 0.10")
    void sharedPriorRatioMin() {
        assertEquals(0.10, SharedPoolConfig.SHARED_PRIOR_RATIO_MIN, 0.001);
    }

    @Test
    @DisplayName("SHARED_PRIOR_RATIO_MAX is 0.30")
    void sharedPriorRatioMax() {
        assertEquals(0.30, SharedPoolConfig.SHARED_PRIOR_RATIO_MAX, 0.001);
    }

    @Test
    @DisplayName("TOTAL_DRIVE is 1.0")
    void totalDrive() {
        assertEquals(1.0, SharedPoolConfig.TOTAL_DRIVE, 0.001);
    }

    @Test
    @DisplayName("min <= ratio <= max constraint holds")
    void ratioWithinBounds() {
        assertTrue(SharedPoolConfig.SHARED_PRIOR_RATIO_MIN <= SharedPoolConfig.SHARED_PRIOR_RATIO);
        assertTrue(SharedPoolConfig.SHARED_PRIOR_RATIO <= SharedPoolConfig.SHARED_PRIOR_RATIO_MAX);
    }
}
