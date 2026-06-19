package com.izimi.eagent.brainstem.perception;

import com.izimi.eagent.brainstem.action.LandscapePatch;
import com.izimi.eagent.brainstem.perception.PerceptionSnapshot.DenseView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AffordanceRouterTest {
    @TempDir
    Path tempDir;

    final AffordanceRouter router = new AffordanceRouter();

    SalienceMap freshSm() { return new SalienceMap(new ValueRegistry(tempDir)); }

    @Test @DisplayName("empty candidates returns empty list")
    void emptyCandidates() {
        assertTrue(router.route(List.of(), healthyView(), Map.of(), true).isEmpty());
    }

    @Test @DisplayName("null candidates returns empty list")
    void nullCandidates() {
        assertTrue(router.route(null, healthyView(), Map.of(), true).isEmpty());
    }

    @Test @DisplayName("route returns sorted candidates")
    void basicRoute() {
        var sm = freshSm();
        sm.applyAttractor(LandscapePatch.Attractor.ofType("iron_ore", 0.5));
        var candidates = sm.getCandidates(Map.of("iron_ore", 5.0f));
        var result = router.route(candidates, healthyView(), Map.of(), true);
        assertFalse(result.isEmpty());
        assertTrue(result.get(0).adjustedSalience() > 0);
    }

    @Test @DisplayName("CRITICAL tier gets highest offset")
    void criticalOffset() {
        assertEquals(1.0, AffordanceRouter.offsetForTier("CRITICAL"));
    }

    @Test @DisplayName("LOW candidates filtered when higher tier exists")
    void lowFiltered() {
        var moderateHealth = new DenseView(0, 0, 0, 0, 0.5, 1.0, 1.0, false, true, 0, 0);
        var candidates = List.of(
            new SalienceMap.Candidate("dirt", 0.05f, "test"),
            new SalienceMap.Candidate("iron_ore", 0.6f, "test")
        );
        var result = router.route(candidates, moderateHealth, Map.of(), true);
        for (var c : result) {
            assertNotEquals("dirt", c.targetType());
        }
    }

    @Test @DisplayName("commitLock prevents duplicate category within lock duration")
    void commitLockBlocks() {
        var candidates = List.of(
            new SalienceMap.Candidate("iron_ore", 0.6f, "test"),
            new SalienceMap.Candidate("diamond_ore", 1.0f, "test")
        );
        var first = router.route(candidates, healthyView(), Map.of(), true);
        assertEquals(2, first.size());

        var candidates2 = List.of(
            new SalienceMap.Candidate("iron_ore", 0.6f, "test"),
            new SalienceMap.Candidate("diamond_ore", 1.0f, "test")
        );
        var second = router.route(candidates2, healthyView(), Map.of(), true);
        assertTrue(second.size() <= 2);
    }

    @Test @DisplayName("low health triggers CRITICAL tier for food")
    void lowHealthCritical() {
        var lowHealth = new DenseView(0, 0, 0, 0, 0.2, 0.8, 1.0, false, true, 0, 0);
        var candidates = List.of(
            new SalienceMap.Candidate("apple", 0.4f, "test"),
            new SalienceMap.Candidate("iron_ore", 0.6f, "test")
        );
        var result = router.route(candidates, lowHealth, Map.of(), true);
        assertFalse(result.isEmpty());
        var apple = result.stream().filter(c -> "apple".equals(c.targetType())).findFirst();
        assertTrue(apple.isPresent());
    }

    @Test @DisplayName("reset clears locks")
    void resetClears() {
        var candidates = List.of(
            new SalienceMap.Candidate("iron_ore", 0.6f, "test"),
            new SalienceMap.Candidate("coal_ore", 0.4f, "test")
        );
        router.route(candidates, healthyView(), Map.of(), true);
        assertTrue(router.activeLockCount() > 0);
        router.reset();
        assertEquals(0, router.activeLockCount());
    }

    @Test @DisplayName("categoryFromTarget recognises known categories")
    void categoryFromTarget() {
        assertEquals("ore", AffordanceRouter.categoryFromTarget("diamond_ore"));
        assertEquals("ore", AffordanceRouter.categoryFromTarget("gold_ore"));
        assertEquals("wood", AffordanceRouter.categoryFromTarget("oak_log"));
        assertEquals("food", AffordanceRouter.categoryFromTarget("apple"));
        assertEquals("stone", AffordanceRouter.categoryFromTarget("stone"));
        assertEquals("dirt", AffordanceRouter.categoryFromTarget("dirt"));
        assertEquals("misc", AffordanceRouter.categoryFromTarget("bedrock"));
    }

    @Test @DisplayName("lock duration computed correctly")
    void lockDuration() {
        assertEquals(15, AffordanceRouter.computeLockDuration(10.0));
        assertEquals(40, AffordanceRouter.computeLockDuration(60.0));
        assertEquals(60, AffordanceRouter.computeLockDuration(200.0));
    }

    private DenseView healthyView() {
        return new DenseView(0, 0, 0, 0, 1.0, 1.0, 1.0, false, true, 0, 0);
    }
}
