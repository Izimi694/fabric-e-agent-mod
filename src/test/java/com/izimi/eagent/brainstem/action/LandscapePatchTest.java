package com.izimi.eagent.brainstem.action;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LandscapePatchTest {

    @Test
    @DisplayName("attractor requires exactly one of type/category/semanticLabels")
    void attractorRequiresOne() {
        assertThrows(IllegalArgumentException.class,
            () -> new LandscapePatch.Attractor(null, null, null, 0.5, 0.005, 600));
        assertThrows(IllegalArgumentException.class,
            () -> new LandscapePatch.Attractor("iron", "metal", null, 0.5, 0.005, 600));
        assertDoesNotThrow(() -> LandscapePatch.Attractor.ofType("iron_ore", 0.5));
        assertDoesNotThrow(() -> LandscapePatch.Attractor.ofCategory("edible", 0.5));
        assertDoesNotThrow(() -> LandscapePatch.Attractor.ofLabels(List.of("ancient_debris"), 0.5));
    }

    @Test
    @DisplayName("attractor rejects both type and category")
    void attractorMutuallyExclusive() {
        assertThrows(IllegalArgumentException.class,
            () -> new LandscapePatch.Attractor("iron_ore", "edible", null, 0.5, 0.005, 600));
    }

    @Test
    @DisplayName("semanticLabels is accepted as valid third option")
    void semanticLabels() {
        var a = LandscapePatch.Attractor.ofLabels(List.of("ancient_debris", "netherite"), 0.7);
        assertTrue(a.isSemantic());
        assertFalse(a.isCategory());
        assertEquals(0.7, a.salienceBoost());
    }

    @Test
    @DisplayName("attractor clamps salienceBoost to [0,1]")
    void attractorClamps() {
        var a = LandscapePatch.Attractor.ofType("test", 2.0);
        assertEquals(1.0, a.salienceBoost());
        var low = LandscapePatch.Attractor.ofType("test", -0.5);
        assertEquals(0.0, low.salienceBoost());
    }

    @Test
    @DisplayName("attractor isCategory returns true only for category type")
    void isCategory() {
        assertFalse(LandscapePatch.Attractor.ofType("iron_ore", 0.3).isCategory());
        assertTrue(LandscapePatch.Attractor.ofCategory("edible", 0.3).isCategory());
    }

    @Test
    @DisplayName("repulsor also rejects both type and category")
    void repulsorMutuallyExclusive() {
        assertThrows(IllegalArgumentException.class,
            () -> new LandscapePatch.Repulsor("creeper", "hostile", 0.5, 0.005, 600));
    }

    @Test
    @DisplayName("patch requires at least one of attractor/repulsor")
    void patchRequiresOne() {
        assertThrows(IllegalArgumentException.class,
            LandscapePatch::empty);
    }

    @Test
    @DisplayName("targetKey returns type or category")
    void targetKey() {
        var attractPatch = LandscapePatch.attractOnly(
            LandscapePatch.Attractor.ofType("iron_ore", 0.3));
        assertEquals("iron_ore", attractPatch.targetKey());

        var catPatch = LandscapePatch.attractOnly(
            LandscapePatch.Attractor.ofCategory("edible", 0.3));
        assertEquals("edible", catPatch.targetKey());
    }

    @Test
    @DisplayName("conditionSpec clamps multiplier")
    void conditionMultiplier() {
        var c = new LandscapePatch.ConditionSpec("HEALTH_BELOW", "", 0.5, false, 0.1);
        assertEquals(0.5, c.thenMultiplier());
        var high = new LandscapePatch.ConditionSpec("HEALTH_BELOW", "", 0.5, false, 10.0);
        assertEquals(5.0, high.thenMultiplier());
    }
}
