package com.izimi.eagent.brainstem.perception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PerceptionSnapshotTest {

    @Test
    @DisplayName("empty snapshot has safe default values")
    void empty() {
        var snap = PerceptionSnapshot.empty(100);
        assertEquals(100, snap.tick());
        assertEquals(1.0, snap.dense().health());
        assertEquals(0, snap.dense().oreVeinsNearby());
        assertTrue(snap.dense().hasShelterNearby());
    }

    @Test
    @DisplayName("DenseView clamps health/hunger/armor to [0, 1]")
    void clampsVitals() {
        var dv = new PerceptionSnapshot.DenseView(0, 0, 0, 0, -0.5, 1.5, 2.0, false, true, 0, 0);
        assertEquals(0.0, dv.health());
        assertEquals(1.0, dv.hunger());
        assertEquals(1.0, dv.armor());
    }

    @Test
    @DisplayName("DenseView clamps non-negative counts")
    void clampsCounts() {
        var dv = new PerceptionSnapshot.DenseView(-5, -3, -1, 0, 1.0, 1.0, 1.0, false, true, 0, 0);
        assertEquals(0, dv.oreVeinsNearby());
        assertEquals(0, dv.woodBlocksNearby());
        assertEquals(0, dv.mobCount());
    }

    @Test
    @DisplayName("CompactView stores non-null summary")
    void compactView() {
        var cv = new PerceptionSnapshot.CompactView("iron nearby");
        assertEquals("iron nearby", cv.summary());
        var empty = new PerceptionSnapshot.CompactView(null);
        assertEquals("", empty.summary());
    }
}
