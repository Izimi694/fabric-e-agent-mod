package com.izimi.aiplayermod.brainstem.scheduler;

import java.util.*;

public class ParameterBinder {

    public static class BindingError extends RuntimeException {
        public BindingError(String message) { super(message); }
    }

    public record BindingDef(String from, String to, String transform) {}

    public static Map<String, Object> bindParameters(
            List<BindingDef> bindings,
            Map<String, Object> upstreamOutputs,
            Map<String, Object> taskContext) {
        Map<String, Object> params = new HashMap<>();
        for (BindingDef b : bindings) {
            Object value = resolveFrom(b.from(), upstreamOutputs, taskContext);
            if (value == null) {
                throw new BindingError("Missing upstream output: " + b.from());
            }
            if (b.transform() != null && !b.transform().isEmpty()) {
                value = applyTransform(value, b.transform(), taskContext);
            }
            params.put(b.to(), value);
        }
        return params;
    }

    private static Object resolveFrom(String from, Map<String, Object> upstreamOutputs,
                                      Map<String, Object> taskContext) {
        if (from.startsWith("output.")) {
            String key = from.substring(7);
            return upstreamOutputs.get(key);
        }
        if (from.startsWith("context.")) {
            String key = from.substring(8);
            return taskContext.get(key);
        }
        return upstreamOutputs.get(from);
    }

    private static Object applyTransform(Object value, String transform, Map<String, Object> taskContext) {
        if (value instanceof Number num) {
            if (transform.startsWith("offset:")) {
                double offset = Double.parseDouble(transform.substring(7).trim());
                return num.doubleValue() + offset;
            }
        }
        if (transform.startsWith("nearest(") && value instanceof String target) {
            @SuppressWarnings("unchecked")
            List<Object> candidates = (List<Object>) taskContext.getOrDefault("candidates", List.of());
            if (!candidates.isEmpty()) return candidates.get(0);
        }
        return value;
    }
}
