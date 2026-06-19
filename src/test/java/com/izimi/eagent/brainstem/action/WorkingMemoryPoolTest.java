package com.izimi.eagent.brainstem.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkingMemoryPoolTest {
    @Test @DisplayName("initial inertia is zero")
    void initialZero() {
        var pool = new WorkingMemoryPool();
        assertEquals(0, pool.getInertia("iron_ore"));
    }
    @Test @DisplayName("setInertia caps at MAX_INERTIA")
    void capsMax() {
        var pool = new WorkingMemoryPool();
        pool.setInertia("iron_ore", 2.0);
        assertEquals(WorkingMemoryPool.MAX_INERTIA, pool.getInertia("iron_ore"));
    }
    @Test @DisplayName("tick decays inertia by 0.7")
    void decays() {
        var pool = new WorkingMemoryPool();
        pool.setInertia("iron_ore", 0.5);
        pool.tick();
        assertEquals(0.35, pool.getInertia("iron_ore"), 1e-6);
    }
    @Test @DisplayName("tick removes inertia below 0.001")
    void removesSmall() {
        var pool = new WorkingMemoryPool();
        pool.setInertia("iron_ore", 0.001);
        pool.tick();
        assertEquals(0, pool.getInertia("iron_ore"));
    }
    @Test @DisplayName("setInertia ignores null/blank")
    void ignoresNull() {
        var pool = new WorkingMemoryPool();
        pool.setInertia(null, 0.5);
        assertNull(pool.getLastActionType());
    }
    @Test @DisplayName("lastActionType tracks last set call")
    void tracksLast() {
        var pool = new WorkingMemoryPool();
        pool.setInertia("coal_ore", 0.3);
        assertEquals("coal_ore", pool.getLastActionType());
    }
    @Test @DisplayName("reset clears all state including trajectory and schemas")
    void resets() {
        var pool = new WorkingMemoryPool();
        pool.setInertia("iron_ore", 0.5);
        pool.recordPosition(10, 20, 0.5f);
        pool.reset();
        assertEquals(0, pool.getInertia("iron_ore"));
        assertNull(pool.getLastActionType());
        assertTrue(pool.getAllInertias().isEmpty());
        assertEquals(0, pool.getTrajectoryBufferSize());
        assertTrue(pool.getActiveSchemas().isEmpty());
    }

    // ── Schema compression tests ──

    @Test @DisplayName("recordPosition stores one point")
    void recordPositionStoresPoint() {
        var pool = new WorkingMemoryPool();
        pool.recordPosition(10, 20, 0.8f);
        assertEquals(1, pool.getTrajectoryBufferSize());
    }

    @Test @DisplayName("buffer fills to 1200 triggers auto-compress")
    void autoCompressAt1200() {
        var pool = new WorkingMemoryPool();
        for (int i = 0; i < WorkingMemoryPool.MAX_TRAJECTORY_POINTS; i++) {
            pool.recordPosition(i % 16, (i * 2) % 16, 0.5f);
        }
        assertTrue(pool.getTrajectoryBufferSize() < WorkingMemoryPool.MAX_TRAJECTORY_POINTS);
    }

    @Test @DisplayName("buffer < 100 returns no schemas from compress")
    void compressEarlyReturn() {
        var pool = new WorkingMemoryPool();
        for (int i = 0; i < 50; i++) {
            pool.recordPosition(i, i, 0.3f);
        }
        assertTrue(pool.getActiveSchemas().isEmpty());
    }

    @Test @DisplayName("compress produces top schemas sorted by heat")
    void compressTopSchemas() {
        var pool = new WorkingMemoryPool();
        // 200 points: 100 in cell (0,0) with high sat, 50 in (1,1), 50 in (2,2)
        for (int i = 0; i < 100; i++) pool.recordPosition(1.0, 1.0, 0.9f);
        for (int i = 0; i < 50; i++) pool.recordPosition(5.0, 5.0, 0.5f);
        for (int i = 0; i < 50; i++) pool.recordPosition(9.0, 9.0, 0.3f);

        var result = pool.compressTrajectoryToSchemas();
        assertFalse(result.isEmpty());
        assertTrue(result.get(0).avgSatisfaction() >= result.get(result.size() - 1).avgSatisfaction());
        // Cell (0,0) should be hottest
        assertEquals(1, result.stream().filter(s -> s.gridX() == 0 && s.gridZ() == 0).count());
    }

    @Test @DisplayName("getSchemaForPosition returns schema inside grid cell")
    void schemaForPosition() {
        var pool = new WorkingMemoryPool();
        for (int i = 0; i < 150; i++) pool.recordPosition(1.5, 1.5, 0.7f);
        pool.compressTrajectoryToSchemas();

        Schema found = pool.getSchemaForPosition(2.0, 1.0);
        assertNotNull(found);
        assertEquals(0, found.gridX());
        assertEquals(0, found.gridZ());
    }

    @Test @DisplayName("getSchemaForPosition returns null outside all regions")
    void schemaForPositionNotFound() {
        var pool = new WorkingMemoryPool();
        for (int i = 0; i < 150; i++) pool.recordPosition(1.5, 1.5, 0.7f);
        pool.compressTrajectoryToSchemas();

        assertNull(pool.getSchemaForPosition(100, 100));
    }

    @Test @DisplayName("inertia bonus applies when inside schema region")
    void inertiaBonusInSchema() {
        var pool = new WorkingMemoryPool();
        pool.setInertia("iron_ore", 0.3);
        // Record positions inside schema cell (0,0): coords 0-3
        for (int i = 0; i < 150; i++) pool.recordPosition(1.0 + i * 0.01, 1.0, 0.7f);
        pool.compressTrajectoryToSchemas();

        // Record one more point inside the schema cell
        pool.recordPosition(2.0, 2.0, 0.5f);
        // lastKnownX/Z should be (2, 2) which falls in cell (0, 0)
        double inertia = pool.getInertia("iron_ore");
        assertTrue(inertia > 0.3);
        assertTrue(inertia <= WorkingMemoryPool.MAX_INERTIA);
    }

    @Test @DisplayName("no inertia bonus when outside all schemas")
    void noInertiaBonusOutside() {
        var pool = new WorkingMemoryPool();
        pool.setInertia("iron_ore", 0.3);
        for (int i = 0; i < 150; i++) pool.recordPosition(1.0, 1.0, 0.7f);
        pool.compressTrajectoryToSchemas();

        // lastKnownX/Z is still (1.0, 1.0) from last recordPosition in compress
        // Now record outside
        pool.recordPosition(100, 100, 0.5f);
        assertEquals(0.3, pool.getInertia("iron_ore"), 1e-6);
    }

    @Test @DisplayName("consecutive cycles consolidate schemas")
    void consolidationOverCycles() {
        var pool = new WorkingMemoryPool();
        // Cycle 1
        for (int i = 0; i < 150; i++) pool.recordPosition(1.0, 1.0, 0.7f);
        pool.compressTrajectoryToSchemas();
        assertEquals(1, pool.getActiveSchemas().size());
        assertEquals(1, pool.getActiveSchemas().get(0).consecutiveHiCount());

        // Cycle 2
        for (int i = 0; i < 150; i++) pool.recordPosition(1.0, 1.0, 0.7f);
        pool.compressTrajectoryToSchemas();
        assertEquals(2, pool.getActiveSchemas().get(0).consecutiveHiCount());

        // Cycle 3 → should become permanent
        for (int i = 0; i < 150; i++) pool.recordPosition(1.0, 1.0, 0.7f);
        pool.compressTrajectoryToSchemas();
        assertEquals(3, pool.getActiveSchemas().get(0).consecutiveHiCount());
        assertTrue(pool.getActiveSchemas().get(0).permanent());
    }

    @Test @DisplayName("forgotten schema removed after 2 missed cycles")
    void forgetting() {
        var pool = new WorkingMemoryPool();
        // Cycle 1: create schema at (0,0)
        for (int i = 0; i < 150; i++) pool.recordPosition(1.0, 1.0, 0.7f);
        pool.compressTrajectoryToSchemas();
        assertEquals(1, pool.getActiveSchemas().size());

        // Cycle 2: move to new area, old schema goes to lo=1
        for (int i = 0; i < 150; i++) pool.recordPosition(100, 100, 0.7f);
        pool.compressTrajectoryToSchemas();
        // Both schemas should exist (old one decaying)
        assertEquals(2, pool.getActiveSchemas().size());
        var oldSchema = pool.getActiveSchemas().stream()
            .filter(s -> s.gridX() == 0 && s.gridZ() == 0).findFirst();
        assertTrue(oldSchema.isPresent());
        assertEquals(1, oldSchema.get().consecutiveLoCount());

        // Cycle 3: stay in new area, old schema goes to lo=2 → removed
        for (int i = 0; i < 150; i++) pool.recordPosition(100, 100, 0.7f);
        pool.compressTrajectoryToSchemas();
        var stillPresent = pool.getActiveSchemas().stream()
            .anyMatch(s -> s.gridX() == 0 && s.gridZ() == 0);
        assertFalse(stillPresent);
    }
}
