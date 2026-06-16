package com.izimi.eagent.brainstem.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record FailureContext(String commandType, String reason, Map<String, Object> diagnostics) {

    public FailureContext {
        diagnostics = diagnostics == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
    }

    public static FailureContext of(String commandType, String reason) {
        return new FailureContext(commandType, reason, Collections.emptyMap());
    }

    public static FailureContext of(String commandType, String reason, Map<String, Object> diagnostics) {
        return new FailureContext(commandType, reason, diagnostics);
    }
}
