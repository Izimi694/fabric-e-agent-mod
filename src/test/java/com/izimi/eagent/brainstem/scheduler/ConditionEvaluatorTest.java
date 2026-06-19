package com.izimi.eagent.brainstem.scheduler;

import com.izimi.eagent.brainstem.perception.PerceptionSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

class ConditionEvaluatorTest {
    final PerceptionSnapshot lowHealth = new PerceptionSnapshot(0,
        new PerceptionSnapshot.DenseView(0, 0, 0, 0, 0.2, 0.8, 1.0, false, true, 0, 0),
        new PerceptionSnapshot.CompactView(""));
    final PerceptionSnapshot fullHealth = new PerceptionSnapshot(0,
        new PerceptionSnapshot.DenseView(0, 0, 0, 0, 1.0, 0.8, 1.0, false, true, 0, 0),
        new PerceptionSnapshot.CompactView(""));
    final PerceptionSnapshot attacked = new PerceptionSnapshot(0,
        new PerceptionSnapshot.DenseView(0, 0, 5, 1, 0.7, 0.8, 1.0, true, false, 0, 0),
        new PerceptionSnapshot.CompactView(""));
    final ConditionEvaluator eval = new ConditionEvaluator();

    @Test @DisplayName("HEALTH_BELOW detects low health")
    void healthBelow() {
        var cond = new ConditionEvaluator.Condition(ConditionEvaluator.ConditionType.HEALTH_BELOW, 0.3, null);
        assertTrue(eval.evaluate(cond, lowHealth, Map.of()));
        assertFalse(eval.evaluate(cond, fullHealth, Map.of()));
    }
    @Test @DisplayName("UNDER_ATTACK detects attack flag")
    void underAttack() {
        var cond = new ConditionEvaluator.Condition(ConditionEvaluator.ConditionType.UNDER_ATTACK, 0, null);
        assertTrue(eval.evaluate(cond, attacked, Map.of()));
        assertFalse(eval.evaluate(cond, lowHealth, Map.of()));
    }
    @Test @DisplayName("HAS_SHELTER detects shelter flag")
    void hasShelter() {
        var cond = new ConditionEvaluator.Condition(ConditionEvaluator.ConditionType.HAS_SHELTER, 0, null);
        assertTrue(eval.evaluate(cond, lowHealth, Map.of()));
        assertFalse(eval.evaluate(cond, attacked, Map.of()));
    }
    @Test @DisplayName("MOB_THREAT checks mob count threshold")
    void mobThreat() {
        var cond = new ConditionEvaluator.Condition(ConditionEvaluator.ConditionType.MOB_THREAT, 2, "hostile");
        assertTrue(eval.evaluate(cond, attacked, Map.of()));
        var noMobs = new PerceptionSnapshot(0,
            new PerceptionSnapshot.DenseView(0, 0, 0, 0, 1.0, 1.0, 1.0, false, true, 0, 0),
            new PerceptionSnapshot.CompactView(""));
        assertFalse(eval.evaluate(cond, noMobs, Map.of()));
    }
    @Test @DisplayName("RESOURCE_ABUNDANT checks resource count")
    void resourceAbundant() {
        var cond = new ConditionEvaluator.Condition(ConditionEvaluator.ConditionType.RESOURCE_ABUNDANT, 5, "iron_ore");
        assertTrue(eval.evaluate(cond, lowHealth, Map.of("iron_ore", 8)));
        assertFalse(eval.evaluate(cond, lowHealth, Map.of("iron_ore", 2)));
    }
    @Test @DisplayName("returns false for null condition or snapshot")
    void nullSafety() {
        assertFalse(eval.evaluate(null, lowHealth, Map.of()));
        assertFalse(eval.evaluate(new ConditionEvaluator.Condition(ConditionEvaluator.ConditionType.HEALTH_BELOW, 0.5, null), null, Map.of()));
    }
}
