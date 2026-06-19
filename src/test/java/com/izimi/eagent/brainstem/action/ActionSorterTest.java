package com.izimi.eagent.brainstem.action;

import com.izimi.eagent.brainstem.perception.AffordanceRouter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class ActionSorterTest {
    final WorkingMemoryPool pool = new WorkingMemoryPool();
    final ActionSorter sorter = new ActionSorter(pool);

    @Test @DisplayName("empty candidates returns NONE")
    void emptyReturnsNone() {
        assertEquals(BlendedAction.NONE, sorter.select(List.of(), 0, 0.5, 0));
    }

    @Test @DisplayName("null candidates returns NONE")
    void nullReturnsNone() {
        assertEquals(BlendedAction.NONE, sorter.select(null, 0, 0.5, 0));
    }

    @Test @DisplayName("selects candidate with highest salience")
    void selectsHighest() {
        var candidates = List.of(
            new AffordanceRouter.SortedCandidate("dirt", 0.3, 0, "NORMAL"),
            new AffordanceRouter.SortedCandidate("iron_ore", 0.8, 0, "NORMAL"),
            new AffordanceRouter.SortedCandidate("coal_ore", 0.4, 0, "NORMAL")
        );
        var result = sorter.select(candidates, 0, 0.5, 0);
        assertEquals("iron_ore", result.targetType());
    }

    @Test @DisplayName("working memory inertia biases toward last action")
    void inertiaBias() {
        pool.setInertia("coal_ore", 0.5);
        var candidates = List.of(
            new AffordanceRouter.SortedCandidate("dirt", 0.6, 0, "NORMAL"),
            new AffordanceRouter.SortedCandidate("coal_ore", 0.5, 0, "NORMAL")
        );
        var result = sorter.select(candidates, 0, 0.5, 0);
        assertEquals("coal_ore", result.targetType());
    }

    @Test @DisplayName("returns last action from inertia when no candidates")
    void returnsLastAction() {
        pool.setInertia("iron_ore", 0.4);
        var result = sorter.select(List.of(), 0, 0.5, 0);
        assertEquals("iron_ore", result.targetType());
        assertEquals(0.4, result.weight(), 1e-6);
    }

    @Test @DisplayName("computeGain decreases with higher serotonin")
    void computeGain() {
        assertEquals(1.0, ActionSorter.computeGain(0), 1e-6);
        assertEquals(0.5, ActionSorter.computeGain(1), 1e-6);
        assertTrue(ActionSorter.computeGain(10) < 0.5);
    }

    @Test @DisplayName("clampTemperature stays within bounds")
    void clampTemperature() {
        assertEquals(0.05, ActionSorter.clampTemperature(-1, 0));
        assertEquals(0.8, ActionSorter.clampTemperature(0.5, 0.5));
        assertEquals(0.5, ActionSorter.clampTemperature(0.25, 0.25));
    }

    @Test @DisplayName("softmax returns valid probability distribution")
    void softmaxValid() {
        var candidates = List.of(
            new ActionSorter.ExcitementCandidate("a", 0.8),
            new ActionSorter.ExcitementCandidate("b", 0.4),
            new ActionSorter.ExcitementCandidate("c", 0.1)
        );
        double[] probs = ActionSorter.softmax(candidates, 1.0, 0.5);
        assertEquals(3, probs.length);
        double sum = 0;
        for (double p : probs) sum += p;
        assertEquals(1.0, sum, 1e-6);
        assertTrue(probs[0] > probs[1]);
        assertTrue(probs[1] > probs[2]);
    }

    @Test @DisplayName("softmax with zero temperature picks max")
    void softmaxZeroTemp() {
        var candidates = List.of(
            new ActionSorter.ExcitementCandidate("a", 0.3),
            new ActionSorter.ExcitementCandidate("b", 0.8),
            new ActionSorter.ExcitementCandidate("c", 0.1)
        );
        double[] probs = ActionSorter.softmax(candidates, 1.0, 0.001);
        assertEquals(1.0, probs[1], 1e-6);
    }
}
