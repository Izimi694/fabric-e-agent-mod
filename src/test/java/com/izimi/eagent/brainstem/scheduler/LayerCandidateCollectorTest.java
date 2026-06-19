package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.perception.PerceptionSnapshot;
import com.izimi.eagent.brainstem.perception.SalienceMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class LayerCandidateCollectorTest {
    final LayerCandidateCollector collector = new LayerCandidateCollector();

    @Test @DisplayName("collect with all null returns empty")
    void allNull() {
        var result = collector.collect(null, null, null, null, null, null);
        assertTrue(result.isEmpty());
    }

    @Test @DisplayName("collect L4 from snapshot with low health")
    void l4LowHealth() {
        var snap = new PerceptionSnapshot(0,
            new PerceptionSnapshot.DenseView(0, 0, 0, 0, 0.2, 0.8, 1.0, false, true, 0, 0),
            new PerceptionSnapshot.CompactView(""));
        var result = collector.collect(null, null, null, null, snap, null);
        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(c -> "heal".equals(c.targetType())));
    }

    @Test @DisplayName("collect L4 from snapshot with many mobs")
    void l4ManyMobs() {
        var snap = new PerceptionSnapshot(0,
            new PerceptionSnapshot.DenseView(0, 0, 5, 0, 1.0, 1.0, 1.0, false, true, 0, 0),
            new PerceptionSnapshot.CompactView(""));
        var result = collector.collect(null, null, null, null, snap, null);
        assertTrue(result.stream().anyMatch(c -> "combat".equals(c.targetType())));
    }

    @Test @DisplayName("collect L4 from snapshot with projectiles")
    void l4Projectiles() {
        var snap = new PerceptionSnapshot(0,
            new PerceptionSnapshot.DenseView(0, 0, 0, 2, 1.0, 1.0, 1.0, false, true, 0, 0),
            new PerceptionSnapshot.CompactView(""));
        var result = collector.collect(null, null, null, null, snap, null);
        assertTrue(result.stream().anyMatch(c -> "evade".equals(c.targetType())));
    }

    @Test @DisplayName("candidates have correct source labels")
    void sourceLabels() {
        var snap = new PerceptionSnapshot(0,
            new PerceptionSnapshot.DenseView(0, 0, 0, 2, 0.2, 1.0, 1.0, false, true, 0, 0),
            new PerceptionSnapshot.CompactView(""));
        var result = collector.collect(null, null, null, null, snap, null);
        for (var c : result) {
            assertEquals("L4", c.source());
        }
    }
}
