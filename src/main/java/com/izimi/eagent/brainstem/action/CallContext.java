package com.izimi.eagent.brainstem.action;

import java.util.List;

public record CallContext(
    String personaProfile,
    CompressedState state,
    List<MemoryHighlight> highlights,
    PerformanceReport stats
) {
    /** 硬 Token 预算上限, 含 JSON 结构膨胀余量 */
    public static final int MAX_TOKENS = 1024;

    public CallContext {
        personaProfile = personaProfile == null ? "" : personaProfile;
    }

    public static CallContext empty() {
        return new CallContext("", CompressedState.idle(), List.of(), PerformanceReport.empty());
    }

    /** 报告当前预估 Token 数, 超过 MAX_TOKENS 需截断 */
    public int reportTokenUsage() {
        int sum = personaProfile.length() / 2;
        sum += state.activeGoalSummary().length() / 2;
        for (var h : highlights) sum += h.summary().length() / 2;
        return sum;
    }

    public boolean exceedsTokenBudget() {
        return reportTokenUsage() > MAX_TOKENS;
    }
}
