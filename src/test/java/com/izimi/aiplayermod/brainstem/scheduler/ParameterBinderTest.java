package com.izimi.aiplayermod.brainstem.scheduler;

import com.izimi.aiplayermod.brainstem.scheduler.ParameterBinder.BindingDef;
import com.izimi.aiplayermod.brainstem.scheduler.ParameterBinder.BindingError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ParameterBinderTest {

    @Test
    @DisplayName("bindParameters passes through direct values")
    void bindParametersDirect() {
        var bindings = List.of(new BindingDef("output.position", "input.position", null));
        Map<String, Object> upstream = new java.util.HashMap<>();
        upstream.put("position", new int[]{10, 64, 10});
        var result = ParameterBinder.bindParameters(bindings, upstream, Map.of());
        assertArrayEquals(new int[]{10, 64, 10}, (int[]) result.get("input.position"));
    }

    @Test
    @DisplayName("bindParameters reads from context prefix")
    void bindParametersFromContext() {
        var bindings = List.of(new BindingDef("context.target", "input.target", null));
        Map<String, Object> context = new java.util.HashMap<>();
        context.put("target", "diamond");
        var result = ParameterBinder.bindParameters(bindings, Map.of(), context);
        assertEquals("diamond", result.get("input.target"));
    }

    @Test
    @DisplayName("bindParameters applies offset transform")
    void bindParametersOffsetTransform() {
        var bindings = List.of(new BindingDef("output.y", "input.y", "offset:2.5"));
        Map<String, Object> upstream = new java.util.HashMap<>();
        upstream.put("y", 10.0);
        var result = ParameterBinder.bindParameters(bindings, upstream, Map.of());
        assertEquals(12.5, (Double) result.get("input.y"), 0.001);
    }

    @Test
    @DisplayName("bindParameters applies nearest transform")
    void bindParametersNearestTransform() {
        var bindings = List.of(new BindingDef("output.target", "input.best", "nearest()"));
        Map<String, Object> upstream = new java.util.HashMap<>();
        upstream.put("target", "iron_ore");
        Map<String, Object> taskContext = new java.util.HashMap<>();
        taskContext.put("candidates", List.of("diamond_ore", "iron_ore", "coal_ore"));
        var result = ParameterBinder.bindParameters(bindings, upstream, taskContext);
        assertEquals("diamond_ore", result.get("input.best"));
    }

    @Test
    @DisplayName("bindParameters throws BindingError for missing upstream value")
    void bindParametersMissingUpstream() {
        var bindings = List.of(new BindingDef("output.missing", "input.x", null));
        assertThrows(BindingError.class, () ->
                ParameterBinder.bindParameters(bindings, Map.of(), Map.of()));
    }

    @Test
    @DisplayName("bindParameters throws BindingError with message containing missing key")
    void bindParametersErrorContainsKey() {
        var bindings = List.of(new BindingDef("output.xyz", "input.a", null));
        var ex = assertThrows(BindingError.class, () ->
                ParameterBinder.bindParameters(bindings, Map.of(), Map.of()));
        assertTrue(ex.getMessage().contains("xyz"));
    }

    @Test
    @DisplayName("bindParameters handles empty bindings list")
    void bindParametersEmptyBindings() {
        Map<String, Object> upstream = new java.util.HashMap<>();
        upstream.put("x", 1);
        var result = ParameterBinder.bindParameters(List.of(), upstream, Map.of());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("bindParameters applies multiple bindings independently")
    void bindParametersMultiple() {
        var bindings = List.of(
                new BindingDef("output.x", "input.x", null),
                new BindingDef("output.y", "input.y", "offset:-1.0")
        );
        Map<String, Object> upstream = new java.util.HashMap<>();
        upstream.put("x", 5.0);
        upstream.put("y", 3.0);
        var result = ParameterBinder.bindParameters(bindings, upstream, Map.of());
        assertEquals(5.0, (Double) result.get("input.x"), 0.001);
        assertEquals(2.0, (Double) result.get("input.y"), 0.001);
    }

    @Test
    @DisplayName("bindParameters falls back to plain key when no prefix matches")
    void bindParametersPlainKey() {
        var bindings = List.of(new BindingDef("target_block", "input.block", null));
        Map<String, Object> upstream = new java.util.HashMap<>();
        upstream.put("target_block", "stone");
        var result = ParameterBinder.bindParameters(bindings, upstream, Map.of());
        assertEquals("stone", result.get("input.block"));
    }

    @Test
    @DisplayName("BindingDef record stores all fields")
    void bindingDefRecord() {
        var bd = new BindingDef("from_x", "to_y", "offset:5");
        assertEquals("from_x", bd.from());
        assertEquals("to_y", bd.to());
        assertEquals("offset:5", bd.transform());
    }

    @Test
    @DisplayName("BindingError is a RuntimeException")
    void bindingErrorIsRuntime() {
        var ex = new BindingError("test error");
        assertTrue(ex instanceof RuntimeException);
        assertEquals("test error", ex.getMessage());
    }

    @Test
    @DisplayName("bindParameters ignores unknown transform")
    void bindParametersUnknownTransform() {
        var bindings = List.of(new BindingDef("output.val", "input.val", "unknown_transform"));
        Map<String, Object> upstream = new java.util.HashMap<>();
        upstream.put("val", 42);
        var result = ParameterBinder.bindParameters(bindings, upstream, Map.of());
        assertEquals(42, result.get("input.val"));
    }
}
