package com.izimi.eagent.simulation;

import java.util.List;
import com.izimi.eagent.brainstem.scheduler.Perspective;
import com.izimi.eagent.cortex.api.HormonalPreset;

public record Scenario(
    String id,
    String description,
    Perspective domain,
    HormonalPreset hormonalPreset,
    double bayesianConfidence,
    List<ReflexCandidate> candidates,
    String expectedBestId,
    List<String> acceptableIds
) {
    public boolean isExpected(String winnerId) {
        if (winnerId == null) return false;
        if (winnerId.equals(expectedBestId)) return true;
        return acceptableIds != null && acceptableIds.contains(winnerId);
    }
}
