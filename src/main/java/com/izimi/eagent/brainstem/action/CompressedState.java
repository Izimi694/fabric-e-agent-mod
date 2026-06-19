package com.izimi.eagent.brainstem.action;

import com.izimi.eagent.hormonal.NeuroState;

public record CompressedState(
    String currentPerspective,
    String dominantDomain,
    String activeGoalSummary,
    NeuroState hormones,
    int ticksSinceLastDeath,
    double survivalUrgency
) {
    public CompressedState {
        currentPerspective = currentPerspective == null ? "ROUTINE" : currentPerspective;
        dominantDomain = dominantDomain == null ? "IDLE" : dominantDomain;
        activeGoalSummary = activeGoalSummary == null ? "" : activeGoalSummary;
        ticksSinceLastDeath = Math.max(0, ticksSinceLastDeath);
        survivalUrgency = Math.max(0, Math.min(1, survivalUrgency));
    }

    public static CompressedState idle() {
        return new CompressedState("ROUTINE", "IDLE", "", NeuroState.neutral(), 0, 0);
    }
}
