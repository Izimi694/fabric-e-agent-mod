package com.izimi.eagent.brainstem.perception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldScannerTest {

    @Test
    @DisplayName("empty snapshot has safe defaults")
    void emptySnapshot() {
        var snap = PerceptionSnapshot.empty(0);
        assertEquals(0, snap.tick());
        assertEquals(1.0, snap.dense().health());
        assertEquals("", snap.compact().summary());
    }

    @Test
    @DisplayName("getResourceDelta returns 0 for unknown key")
    void resourceDeltaUnknown() {
        var ws = new WorldScanner();
        assertEquals(0, ws.getResourceDelta("nonexistent"));
        assertEquals(0, ws.getResourceCount("nonexistent"));
    }

    @Test
    @DisplayName("getVisibleBlockIds returns empty for null bot")
    void visibleBlocksNull() {
        var ws = new WorldScanner();
        assertTrue(ws.getVisibleBlockIds(null).isEmpty());
    }

    @Test
    @DisplayName("PerceptionSnapshot clamps health to [0,1]")
    void snapshotClamps() {
        var dv = new PerceptionSnapshot.DenseView(0, 0, 0, 0, -0.5, 1.5, 2.0, false, true, 0, 0);
        assertEquals(0.0, dv.health());
        assertEquals(1.0, dv.hunger());
        assertEquals(1.0, dv.armor());
    }

    @Test
    @DisplayName("scan handles null bot gracefully")
    void scanNullBot() {
        var ws = new WorldScanner();
        var snap = ws.scan(null, 100, 20.0);
        assertEquals(100, snap.tick());
        assertEquals(1.0, snap.dense().health());
    }

    @Test
    @DisplayName("compact view is never null")
    void compactNonNull() {
        var ws = new WorldScanner();
        var snap = ws.scan(null, 200, 19.0);
        assertNotNull(snap.compact().summary());
    }
}
