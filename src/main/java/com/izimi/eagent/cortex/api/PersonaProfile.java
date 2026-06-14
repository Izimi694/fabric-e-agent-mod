package com.izimi.eagent.cortex.api;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record PersonaProfile(
    String name,
    String systemPrompt,
    Map<String, List<String>> localOverrides,
    String formatHint
) {
    public PersonaProfile {
        if (localOverrides == null) localOverrides = Collections.emptyMap();
        if (formatHint == null) formatHint = "";
    }
}
