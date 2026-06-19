package com.izimi.eagent.brainstem.perception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AffordanceRouterPrecisionTest {
    @Test @DisplayName("precisionForTier returns correct values")
    void precision() {
        assertEquals(0.5, AffordanceRouter.precisionForTier("CRITICAL"));
        assertEquals(1.0, AffordanceRouter.precisionForTier("HIGH"));
        assertEquals(2.0, AffordanceRouter.precisionForTier("NORMAL"));
        assertEquals(3.0, AffordanceRouter.precisionForTier("LOW"));
    }

    @Test @DisplayName("precisionForTier defaults to NORMAL for unknown")
    void unknownTier() {
        assertEquals(2.0, AffordanceRouter.precisionForTier("UNKNOWN"));
    }
}
